package com.healthtrack.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.healthtrack.R
import com.healthtrack.databinding.ActivityMainBinding
import com.healthtrack.utils.UserProfileManager

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
    }

    private lateinit var binding: ActivityMainBinding
    lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: "aravindh"

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
