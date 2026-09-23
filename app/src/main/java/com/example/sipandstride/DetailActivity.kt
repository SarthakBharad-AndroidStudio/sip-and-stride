package com.example.sipandstride

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

/**
 * Shows one walk and offers the three actions that leave the app: map, share, delete.
 */
class DetailActivity : AppCompatActivity() {

    private var session: WalkSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        title = getString(R.string.detail_title)

        // The object that MainActivity put into the intent.
        val received = intent.getSerializableExtra(MainActivity.EXTRA_SESSION, WalkSession::class.java)

        if (received == null) {
            // Defensive: without data there is nothing to show, so close the screen.
            finish()
            return
        }

        session = received

        findViewById<TextView>(R.id.detailSteps).text =
            getString(R.string.detail_steps, received.steps)

        findViewById<TextView>(R.id.detailDistance).text =
            getString(R.string.detail_distance, received.meters)

        findViewById<TextView>(R.id.detailDate).text =
            getString(R.string.detail_date, received.formattedDate())

        findViewById<TextView>(R.id.detailStart).text = getString(
            R.string.start_point,
            received.startAddress.ifEmpty { getString(R.string.unknown_place) }
        )

        findViewById<TextView>(R.id.detailEnd).text = getString(
            R.string.end_point,
            received.endAddress.ifEmpty { getString(R.string.unknown_place) }
        )

        findViewById<Button>(R.id.mapButton).setOnClickListener { showOnMap() }
        findViewById<Button>(R.id.shareButton).setOnClickListener { shareWalk() }
        findViewById<Button>(R.id.deleteButton).setOnClickListener { confirmDelete() }
    }

    /**
     * Implicit intent with the "geo:" scheme (Tutorial 6, exercise 4).
     * Any installed map application can answer it; we do not care which one.
     */
    private fun showOnMap() {
        val current = session ?: return

        val query = if (current.hasLocation()) {
            "geo:0,0?q=${current.startLat},${current.startLon}"
        } else {
            "geo:0,0?q=${Uri.encode(current.startAddress)}"
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(query)))
        } catch (e: ActivityNotFoundException) {
            // An emulator image without Google Maps has no app for this intent.
            // Without the catch the app would crash.
            Snackbar.make(findViewById(R.id.main), R.string.no_map_app, Snackbar.LENGTH_LONG).show()
        }
    }

    /** Second implicit intent: hand a piece of text to any app that can send text. */
    private fun shareWalk() {
        val current = session ?: return

        val text = getString(
            R.string.share_walk_text,
            current.steps,
            current.meters,
            current.formattedDate(),
            current.startAddress.ifEmpty { getString(R.string.unknown_place) }
        )

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)

        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    }

    /**
     * Deleting is irreversible, so it is confirmed first. The id travels back to
     * MainActivity, which owns the storage — this screen never writes to SharedPreferences.
     */
    private fun confirmDelete() {
        val current = session ?: return

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                val data = Intent()
                data.putExtra(MainActivity.EXTRA_DELETED_ID, current.id)

                setResult(RESULT_OK, data)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}