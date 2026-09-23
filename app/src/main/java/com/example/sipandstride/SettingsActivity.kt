package com.example.sipandstride

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

/**
 * A plain read/write round trip against SharedPreferences (Tutorial 6, exercise 5).
 * The values entered here change the water goal and the distance calculation.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var weightInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var cupInput: EditText
    private lateinit var locationSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        title = getString(R.string.settings_title)

        weightInput = findViewById(R.id.weightInput)
        heightInput = findViewById(R.id.heightInput)
        cupInput = findViewById(R.id.cupInput)
        locationSwitch = findViewById(R.id.locationSwitch)

        // Show the stored values so the user edits instead of retyping.
        weightInput.setText(SessionStore.getWeight(this).toString())
        heightInput.setText(SessionStore.getHeight(this).toString())
        cupInput.setText(SessionStore.getCup(this).toString())
        locationSwitch.isChecked = SessionStore.getUseLocation(this)

        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
    }

    private fun save() {
        // toIntOrNull() returns null instead of throwing on an empty or invalid field,
        // so a forgotten input cannot crash the app.
        val weight = weightInput.text.toString().toIntOrNull()
        val height = heightInput.text.toString().toIntOrNull()
        val cup = cupInput.text.toString().toIntOrNull()

        if (weight == null || height == null || cup == null || weight <= 0 || height <= 0 || cup <= 0) {
            Snackbar.make(locationSwitch, R.string.invalid_input, Snackbar.LENGTH_LONG).show()
            return
        }

        SessionStore.saveSettings(this, weight, height, cup, locationSwitch.isChecked)

        Snackbar.make(locationSwitch, R.string.saved, Snackbar.LENGTH_SHORT).show()
        finish()
    }
}