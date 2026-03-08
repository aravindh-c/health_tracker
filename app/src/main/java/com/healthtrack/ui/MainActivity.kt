package com.healthtrack.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.healthtrack.R
import com.healthtrack.databinding.ActivityMainBinding
import com.healthtrack.utils.NotificationHelper
import com.healthtrack.utils.SecurePrefs
import com.healthtrack.utils.UserProfileManager
import com.healthtrack.workers.NotificationScheduler

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
    }

    private lateinit var binding: ActivityMainBinding
    lateinit var userId: String

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) NotificationScheduler.scheduleAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: "aravindh"

        // Remember who is active (used by notification worker to personalise messages)
        SecurePrefs(this).lastActiveUserId = userId

        val profileManager = UserProfileManager(this)
        val profile = profileManager.getProfile(userId)
        supportActionBar?.title = profile?.display_name ?: "Health Track"

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfig = AppBarConfiguration(
            setOf(R.id.foodLogFragment, R.id.reportFragment, R.id.historyFragment, R.id.tipsFragment)
        )
        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNav.setupWithNavController(navController)

        // Create notification channel and schedule daily reminders
        NotificationHelper(this).createChannel()
        setupNotifications()
    }

    private fun setupNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: request POST_NOTIFICATIONS permission if not granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Permission already granted (or pre-Android 13) — schedule notifications
        NotificationScheduler.scheduleAll(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHostFragment.navController.navigate(R.id.settingsFragment)
                true
            }
            R.id.action_switch_user -> {
                finish() // Go back to user selection
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
