package com.lvms.driver.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lvms.driver.databinding.ItemNotificationBinding
import com.lvms.driver.model.NotificationDto
import java.text.SimpleDateFormat
import java.util.Locale

private fun relativeTime(raw: String): CharSequence {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val millis = parser.parse(raw)!!.time
        DateUtils.getRelativeTimeSpanString(
            millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        )
    } catch (e: Exception) {
        raw
    }
}

class NotificationAdapter(
    private val onNotificationClick: (NotificationDto) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private var notifications: List<NotificationDto> = emptyList()

    fun submitList(newNotifications: List<NotificationDto>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: NotificationDto) {
            binding.notifTitleText.text = notification.title
            binding.notifMessageText.text = notification.message
            binding.notifTimestampText.text = relativeTime(notification.createdAt)
            binding.unreadDot.visibility = if (notification.isRead) View.INVISIBLE else View.VISIBLE

            binding.root.setOnClickListener { onNotificationClick(notification) }
        }
    }
}
