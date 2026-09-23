package com.example.sipandstride

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

/**
 * The dashboard. It owns no sensors; it only shows what is stored and starts the other
 * three activities.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var ringView: WaterRingView
    private lateinit var waterText: TextView
    private lateinit var stepsText: TextView
    private lateinit var emptyText: TextView
    private lateinit var addCupButton: Button
    private lateinit var removeCupButton: Button
    private lateinit var walkButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter

    /**
     * Receives the finished walk from WalkActivity.
     * An ActivityResultLauncher must be created BEFORE onCreate() runs (Tutorial 3,
     * exercise 4), which is why it is a property and not a local variable.
     */
    private val walkLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // The two-parameter version of getSerializableExtra() is used because the old
            // one-parameter version is deprecated since API 33: it deserialised the object
            // before the type could be checked, which was a security problem.
            val session = result.data?.getSerializableExtra(EXTRA_SESSION, WalkSession::class.java)

            if (session != null) {
                SessionStore.addSession(this, session)
                refresh()

                Snackbar.make(
                    ringView,
                    getString(R.string.walk_saved, session.steps),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Receives the id of a walk the user deleted in DetailActivity. */
    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deletedId = result.data?.getLongExtra(EXTRA_DELETED_ID, -1L) ?: -1L

            if (deletedId != -1L) {
                SessionStore.deleteSession(this, deletedId)
                refresh()

                Snackbar.make(ringView, R.string.walk_deleted, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Since Android 15 the app draws behind the status and navigation bars. Without
        // this padding the toolbar would sit underneath the clock.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        ringView = findViewById(R.id.ringView)
        waterText = findViewById(R.id.waterText)
        stepsText = findViewById(R.id.stepsText)
        emptyText = findViewById(R.id.emptyText)
        addCupButton = findViewById(R.id.addCupButton)
        removeCupButton = findViewById(R.id.removeCupButton)
        walkButton = findViewById(R.id.walkButton)
        recyclerView = findViewById(R.id.recyclerView)

        // The trailing lambda is the fun interface from the adapter.
        adapter = SessionAdapter(emptyList()) { session -> openDetail(session) }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        addCupButton.setOnClickListener {
            SessionStore.addWater(this, SessionStore.getCup(this))
            refresh()
        }

        removeCupButton.setOnClickListener {
            SessionStore.addWater(this, -SessionStore.getCup(this))
            refresh()
        }

        walkButton.setOnClickListener { openWalk() }
    }

    /**
     * onResume() runs every time this screen becomes visible again, including after
     * returning from SettingsActivity. Refreshing here means a changed body weight is
     * reflected in the goal immediately.
     */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Reads everything from storage and puts it on screen. */
    private fun refresh() {
        val water = SessionStore.getWaterToday(this)
        val goal = SessionStore.goalMl(this)
        val steps = SessionStore.stepsToday(this)
        val cup = SessionStore.getCup(this)

        val percent = if (goal > 0) (water * 100 / goal).coerceAtMost(100) else 0

        ringView.setProgress(percent)
        ringView.setCenterText("$percent%")
        ringView.setCaption(getString(R.string.ring_caption))

        waterText.text = getString(R.string.water_line, water, goal)
        stepsText.text = getString(R.string.steps_line, steps)

        addCupButton.text = getString(R.string.add_cup, cup)
        removeCupButton.text = getString(R.string.remove_cup, cup)

        val sessions = SessionStore.loadSessions(this)
        adapter.submit(sessions)

        emptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Explicit intent to WalkActivity, carrying the data that screen needs. */
    private fun openWalk() {
        val intent = Intent(this, WalkActivity::class.java)
        intent.putExtra(EXTRA_STRIDE, SessionStore.strideMeters(this))
        intent.putExtra(EXTRA_USE_LOCATION, SessionStore.getUseLocation(this))

        walkLauncher.launch(intent)
    }

    /** Explicit intent to DetailActivity, carrying the whole object. */
    private fun openDetail(session: WalkSession) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra(EXTRA_SESSION, session)

        detailLauncher.launch(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itemShare -> {
                shareSummary()
                true
            }

            R.id.itemSettings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Implicit intent: we do not name a target activity, we describe what we want
     * ("send this text somewhere") and Android offers every app that can do it.
     */
    private fun shareSummary() {
        val text = getString(
            R.string.share_summary,
            SessionStore.getWaterToday(this),
            SessionStore.goalMl(this),
            SessionStore.stepsToday(this)
        )

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)

        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    }

    /**
     * Extra keys live in one place so a typo in one activity cannot silently break the
     * other side. Both activities use MainActivity.EXTRA_... .
     */
    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_DELETED_ID = "extra_deleted_id"
        const val EXTRA_STRIDE = "extra_stride"
        const val EXTRA_USE_LOCATION = "extra_use_location"
    }
}