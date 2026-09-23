package com.example.sipandstride

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Records one walk. This is the only screen that touches hardware.
 *
 * Sensor 1 (motion):   Sensor.TYPE_STEP_COUNTER, read through SensorManager.
 *                      If the device has none — the emulator usually has none — the
 *                      accelerometer is used instead and steps are detected in software.
 * Sensor 2 (position): GPS, read through LocationManager.
 *
 * The activity implements both listener interfaces itself, which is the pattern used in
 * Tutorial 6, so "this" can be passed to registerListener() and requestLocationUpdates().
 */
class WalkActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var stepsBig: TextView
    private lateinit var distanceText: TextView
    private lateinit var modeText: TextView
    private lateinit var startAddressText: TextView
    private lateinit var endAddressText: TextView
    private lateinit var stepProgress: ProgressBar
    private lateinit var actionButton: Button
    private lateinit var simulateButton: Button

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var accelerometer: Sensor? = null

    private lateinit var locationManager: LocationManager
    private lateinit var geocoder: Geocoder

    private var walking = false
    private var steps = 0

    /**
     * TYPE_STEP_COUNTER reports the steps since the phone was switched on, not since the
     * app started. The first value is remembered here and subtracted from every later one.
     * -1 means "no first value yet".
     */
    private var stepBaseline = -1f

    // State for the accelerometer fallback
    private var wasAboveThreshold = false
    private var lastStepMillis = 0L

    private var strideMeters = 0.75
    private var useLocation = true

    private var startLat = 0.0
    private var startLon = 0.0
    private var haveStartFix = false
    private var startAddress = ""
    private var endAddress = ""
    private var lastLocation: Location? = null

    /**
     * Asks for several permissions at once and reacts to the answer.
     * The result is a Map<String, Boolean>: permission name -> granted or not.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val anyGranted = granted.values.any { it }

        if (anyGranted) {
            // Even a partial grant is enough to start; startWalk() checks each permission
            // again before it actually uses the matching sensor.
            startWalk()
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // false at this point means the user ticked "don't ask again", or the system
            // blocks the dialog. The only remaining way is the Android app settings.
            showSettingsDialog()
        } else {
            Snackbar.make(actionButton, R.string.permission_denied, Snackbar.LENGTH_LONG).show()
            startWalk()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_walk)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        title = getString(R.string.walk_title)

        stepsBig = findViewById(R.id.stepsBig)
        distanceText = findViewById(R.id.distanceText)
        modeText = findViewById(R.id.modeText)
        startAddressText = findViewById(R.id.startAddressText)
        endAddressText = findViewById(R.id.endAddressText)
        stepProgress = findViewById(R.id.stepProgress)
        actionButton = findViewById(R.id.actionButton)
        simulateButton = findViewById(R.id.simulateButton)

        // Data that came from MainActivity
        strideMeters = intent.getDoubleExtra(MainActivity.EXTRA_STRIDE, 0.75)
        useLocation = intent.getBooleanExtra(MainActivity.EXTRA_USE_LOCATION, true)

        // getSystemService() hands out the system's single instance of a service.
        // The "as" cast is needed because the method returns a general Object.
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        geocoder = Geocoder(this, Locale.GERMANY)

        modeText.text = when {
            stepSensor != null -> getString(R.string.sensor_mode_counter)
            accelerometer != null -> getString(R.string.sensor_mode_accelerometer)
            else -> getString(R.string.sensor_mode_none)
        }

        actionButton.setOnClickListener {
            if (walking) stopWalkAndReturn() else checkPermissionsThenStart()
        }

        // Only for testing on the emulator, where no real walking happens.
        simulateButton.setOnClickListener {
            if (walking) {
                steps += 10
                updateStepUi()
            }
        }

        updateStepUi()
    }

    // --------------------------------------------------------------- permissions

    /**
     * The permission flow from Tutorial 6:
     *   already granted        -> just start
     *   rationale needed       -> explain first, then ask
     *   otherwise              -> ask directly
     */
    private fun checkPermissionsThenStart() {
        val missing = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (missing.isEmpty()) {
            startWalk()
            return
        }

        val needsExplanation =
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)

        if (needsExplanation) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    permissionLauncher.launch(missing.toTypedArray())
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * When a permission was denied permanently, no app may show the system dialog again.
     * The only thing left is to send the user to the app's settings page with an
     * implicit intent.
     */
    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_settings_title)
            .setMessage(R.string.permission_settings_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> startWalk() }
            .show()
    }

    // ------------------------------------------------------------- start / stop

    private fun startWalk() {
        walking = true
        steps = 0
        stepBaseline = -1f
        haveStartFix = false
        startAddress = ""
        endAddress = ""

        actionButton.setText(R.string.stop)
        updateStepUi()

        registerStepSensor()

        if (useLocation) {
            requestLocation()
        } else {
            startAddressText.text = getString(R.string.start_point, getString(R.string.location_off))
        }
    }

    /**
     * Picks the best available motion sensor.
     * SENSOR_DELAY_UI is a sampling rate of roughly 60 ms — fast enough to see a step,
     * slow enough not to drain the battery. SENSOR_DELAY_FASTEST would be wasteful here.
     */
    private fun registerStepSensor() {
        val counter = stepSensor

        if (counter != null && hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_UI)
            modeText.text = getString(R.string.sensor_mode_counter)
            return
        }

        val accel = accelerometer

        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            modeText.text = getString(R.string.sensor_mode_accelerometer)
        } else {
            modeText.text = getString(R.string.sensor_mode_none)
        }
    }

    /**
     * Builds the WalkSession, hands it back to MainActivity and closes this screen.
     * setResult() + finish() is the "send data back" half of Tutorial 3, exercise 4.
     */
    private fun stopWalkAndReturn() {
        walking = false
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)

        lastLocation?.let { resolveAddress(it, isStart = false) }

        val session = WalkSession(
            id = System.currentTimeMillis(),
            steps = steps,
            meters = (steps * strideMeters).roundToInt(),
            dateMillis = System.currentTimeMillis(),
            startAddress = startAddress,
            endAddress = endAddress,
            startLat = startLat,
            startLon = startLon
        )

        val data = Intent()
        data.putExtra(MainActivity.EXTRA_SESSION, session)

        setResult(RESULT_OK, data)
        finish()
    }

    /**
     * Sensors and GPS keep costing battery until they are unregistered. Doing it in
     * onStop() means they are switched off as soon as the screen is left — the same rule
     * Tutorial 6 applies to the LocationManager.
     *
     * Known limitation to mention in the report: because of this, steps are not counted
     * while the app is in the background. A production app would use a foreground Service.
     */
    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }

    // ------------------------------------------------------------ sensor 1: steps

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !walking) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSinceBoot = event.values[0]

                if (stepBaseline < 0f) {
                    stepBaseline = totalSinceBoot
                }

                steps = (totalSinceBoot - stepBaseline).toInt()
                updateStepUi()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                countStepFromAcceleration(event.values[0], event.values[1], event.values[2])
            }
        }
    }

    /** Required by the interface. Accuracy changes are irrelevant for step counting. */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // nothing to do
    }

    /**
     * Software step detection.
     *
     * The accelerometer reports acceleration on three axes including gravity, so a phone
     * lying still measures about 9.81 m/s². Every step produces a short peak above that.
     * A step is counted when the magnitude crosses the threshold upwards (not while it
     * stays above it) and at least 250 ms have passed, which rules out vibration being
     * counted as dozens of steps.
     */
    private fun countStepFromAcceleration(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()
        val isAbove = magnitude > STEP_THRESHOLD

        if (isAbove && !wasAboveThreshold && now - lastStepMillis > STEP_MIN_INTERVAL_MS) {
            steps++
            lastStepMillis = now
            updateStepUi()
        }

        wasAboveThreshold = isAbove
    }

    private fun updateStepUi() {
        stepsBig.text = steps.toString()
        distanceText.text = getString(R.string.distance_line, (steps * strideMeters).roundToInt())
        stepProgress.progress = (steps * 100 / WALK_TARGET_STEPS).coerceAtMost(100)
    }

    // --------------------------------------------------------- sensor 2: location

    /**
     * @SuppressLint is needed because the compiler cannot see that hasPermission() already
     * checked the permission. The check itself is done — this only silences the warning.
     */
    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            startAddressText.text = getString(R.string.start_point, getString(R.string.location_off))
            return
        }

        // GPS first, mobile network as a backup (Tutorial 6). GPS is exact but needs a
        // clear sky and up to a minute for the first fix; the network provider is instant
        // but only accurate to a few hundred metres.
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER

            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER

            else -> null
        }

        if (provider == null) {
            startAddressText.text = getString(R.string.no_provider)
            return
        }

        startAddressText.text = getString(R.string.start_point, getString(R.string.waiting_for_gps))

        // The cached last position gives an immediate answer while the real fix is computed.
        locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }

        // Update at most every 2 seconds, and only after moving 5 metres.
        locationManager.requestLocationUpdates(provider, 2000L, 5f, this)
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location

        if (!haveStartFix) {
            haveStartFix = true
            startLat = location.latitude
            startLon = location.longitude

            resolveAddress(location, isStart = true)
        }
    }

    /**
     * Turns coordinates into a readable address ("reverse geocoding").
     *
     * Tutorial 6 uses getFromLocation(lat, lon, max), which is deprecated since API 33
     * because it performs a network request on the calling thread and can freeze the UI.
     * The callback version below does the work on a background thread and calls us back,
     * which is why the UI update has to be pushed onto the main thread with runOnUiThread().
     */
    private fun resolveAddress(location: Location, isStart: Boolean) {
        if (!Geocoder.isPresent()) return

        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
            val address = addresses.firstOrNull()

            val text = if (address == null) {
                getString(R.string.unknown_place)
            } else {
                listOfNotNull(address.thoroughfare, address.locality)
                    .joinToString(", ")
                    .ifEmpty { getString(R.string.unknown_place) }
            }

            runOnUiThread {
                if (isStart) {
                    startAddress = text
                    startAddressText.text = getString(R.string.start_point, text)
                } else {
                    endAddress = text
                    endAddressText.text = getString(R.string.end_point, text)
                }
            }
        }
    }

    companion object {
        /** m/s². Resting acceleration is about 9.81, a step peaks clearly above it. */
        private const val STEP_THRESHOLD = 12.5f

        /** Nobody takes more than four steps per second. */
        private const val STEP_MIN_INTERVAL_MS = 250L

        /** Only used to fill the ProgressBar on this screen. */
        private const val WALK_TARGET_STEPS = 1000
    }
}