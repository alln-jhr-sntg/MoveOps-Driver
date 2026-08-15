package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.lvms.driver.R
import com.lvms.driver.databinding.ActivityNotificationsBinding
import com.lvms.driver.model.NotificationDto
import com.lvms.driver.model.NotificationListResponse
import com.lvms.driver.model.StatusResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.NotificationApi
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.parseError
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NotificationsActivity : BaseNavActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private val notificationApi by lazy { ApiClient.retrofit.create(NotificationApi::class.java) }
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        setupBottomNav(binding.bottomNav.bottomNavView, R.id.nav_notifications)
        // Already on Notifications — the bell is a no-op here.
        setupHeader(binding.appHeader.bellButton, binding.appHeader.logoutButton, onBell = {})
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)
        binding.appHeader.root.applyInsetPadding(top = true)
        // No applyInsetPadding(bottom = true) here: BottomNavigationView pads
        // itself for the nav bar inset unconditionally, so padding this
        // container too double-counts it and leaves a dead gap above the
        // system nav bar.

        adapter = NotificationAdapter { notification -> markRead(notification) }
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationsRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }

    private fun loadNotifications() {
        setLoading(true)

        notificationApi.getNotifications("notifications").enqueue(object : Callback<NotificationListResponse> {
            override fun onResponse(call: Call<NotificationListResponse>, response: Response<NotificationListResponse>) {
                setLoading(false)

                if (response.code() == 401) {
                    SessionManager.clear()
                    startActivity(Intent(this@NotificationsActivity, LoginActivity::class.java))
                    finish()
                    return
                }

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    showNotifications(body)
                } else {
                    binding.emptyText.text = response.parseError()
                    binding.emptyText.visibility = View.VISIBLE
                }
            }

            override fun onFailure(call: Call<NotificationListResponse>, t: Throwable) {
                setLoading(false)
                binding.emptyText.text = "Connection failed: ${t.message}"
                binding.emptyText.visibility = View.VISIBLE
            }
        })
    }

    private fun showNotifications(body: NotificationListResponse) {
        NotificationBadgeState.unreadCount = body.unreadCount
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)

        if (body.notifications.isEmpty()) {
            binding.emptyText.text = "You're all caught up."
            binding.emptyText.visibility = View.VISIBLE
            binding.notificationsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.notificationsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(body.notifications)
        }
    }

    private fun markRead(notification: NotificationDto) {
        if (notification.isRead) return

        notificationApi.markRead("notifications/${notification.notificationId}/read")
            .enqueue(object : Callback<StatusResponse> {
                override fun onResponse(call: Call<StatusResponse>, response: Response<StatusResponse>) {
                    if (response.isSuccessful) {
                        loadNotifications()
                    }
                }

                override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                    // Not surfaced — the notification just stays unread until
                    // the next successful tap or screen refresh.
                }
            })
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            binding.emptyText.visibility = View.GONE
            binding.notificationsRecyclerView.visibility = View.GONE
        }
    }
}
