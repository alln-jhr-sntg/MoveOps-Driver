package com.lvms.driver.model

import com.google.gson.annotations.SerializedName

data class NotificationDto(
    @SerializedName("notification_id") val notificationId: Int,
    val title: String,
    val message: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class NotificationListResponse(
    val notifications: List<NotificationDto>,
    @SerializedName("unread_count") val unreadCount: Int
)
