package com.leanway.tranquilstopwatch

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.leanway.tranquilstopwatch.databinding.AboutActivityBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: AboutActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = AboutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val navController = findNavController(R.id.nav_host_fragment_content_about)
        appBarConfiguration = AppBarConfiguration(navController.graph)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_about)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}