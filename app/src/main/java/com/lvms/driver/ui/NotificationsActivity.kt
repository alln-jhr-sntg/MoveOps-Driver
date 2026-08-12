package com.lvms.driver.ui

import android.os.Bundle
import com.lvms.driver.R
import com.lvms.driver.databinding.ActivityNotificationsBinding
import com.lvms.driver.network.SessionManager

// TODO(step 8): wire this up to GET/PATCH /api/notifications once the
// server-side endpoint ships. For now this is just the nav-shell screen so
// the bottom nav has a real destination to point at.
class NotificationsActivity : BaseNavActivity() {

    private lateinit var binding: ActivityNotificationsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        setupBottomNav(binding.bottomNav.root, R.id.nav_notifications)
        setupHeader(binding.appHeader.bellButton, binding.appHeader.logoutButton, onBell = {})
    }
}
