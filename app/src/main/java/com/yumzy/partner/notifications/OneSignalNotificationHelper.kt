package com.yumzy.partner.notifications

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object OneSignalNotificationHelper {
    private const val ONE_SIGNAL_APP_ID = "dabb9362-80ed-4e54-be89-32ffc7dbf383"
    private const val ONE_SIGNAL_REST_API_KEY = "os_v2_app_3k5zgyua5vhfjpujgl74pw7tqonyuq6vbuhuicmwd4v5m3xf3nt32dn5kxntcjqns5a562jfe7f52bl62ttrnhwbledfbnh6wl6c5tq"
    private const val ONE_SIGNAL_API_URL = "https://onesignal.com/api/v1/notifications"
    private val client = OkHttpClient()

    /**
     * Send order status notification (Accepted/Rejected) to a single user
     */
    suspend fun sendOrderStatusNotification(
        userId: String,
        orderId: String,
        newStatus: String,
        restaurantName: String,
        orderAmount: Double? = null
    ) {
        Log.d("OneSignalPartner", "Sending $newStatus notification for userId: $userId, orderId: $orderId")

        try {
            val userDoc = Firebase.firestore.collection("users").document(userId)
                .get().await()
            val playerId = userDoc.getString("oneSignalPlayerId")

            if (playerId == null) {
                Log.w("OneSignalPartner", "No playerId found for userId: $userId")
                return
            }

            val headingText: String
            val contentText: String
            when (newStatus) {
                "Accepted" -> {
                    headingText = "Order Accepted! 🎉"
                    contentText = "Great news! $restaurantName has accepted your order.\nYour food will be ready soon! 😋"
                }
                "Rejected" -> {
                    headingText = "Order Update ⚠️"
                    contentText = "Sorry, $restaurantName couldn't accept your order right now.\nPlease try ordering from another restaurant."
                }
                else -> {
                    headingText = "Order Status Update"
                    contentText = "Your order from $restaurantName is now $newStatus."
                }
            }

            val payload = JSONObject().apply {
                put("app_id", ONE_SIGNAL_APP_ID)
                put("include_player_ids", JSONArray().put(playerId))
                put("headings", JSONObject().put("en", headingText))
                put("contents", JSONObject().put("en", contentText))
                put("data", JSONObject().apply {
                    put("orderId", orderId)
                    put("status", newStatus)
                })
            }

            sendNotificationRequest(payload, "order $orderId")
        } catch (e: Exception) {
            Log.e("OneSignalPartner", "Error sending notification for order $orderId: ${e.message}", e)
        }
    }

    /**
     * Send custom notification to multiple users
     */
    suspend fun sendCustomNotificationToUsers(
        userIds: List<String>,
        message: String,
        restaurantName: String
    ): Boolean {
        Log.d("OneSignalPartner", "Sending custom notification to ${userIds.size} users")

        try {
            val playerIds = mutableListOf<String>()

            // Fetch all player IDs
            for (userId in userIds) {
                try {
                    val userDoc = Firebase.firestore.collection("users").document(userId)
                        .get().await()
                    val playerId = userDoc.getString("oneSignalPlayerId")
                    if (playerId != null) {
                        playerIds.add(playerId)
                    } else {
                        Log.w("OneSignalPartner", "No playerId for userId: $userId")
                    }
                } catch (e: Exception) {
                    Log.e("OneSignalPartner", "Error fetching playerId for userId $userId: ${e.message}")
                }
            }

            if (playerIds.isEmpty()) {
                Log.w("OneSignalPartner", "No valid playerIds found")
                return false
            }

            val payload = JSONObject().apply {
                put("app_id", ONE_SIGNAL_APP_ID)
                put("include_player_ids", JSONArray().apply {
                    playerIds.forEach { put(it) }
                })
                put("headings", JSONObject().put("en", "Message from $restaurantName 📢"))
                put("contents", JSONObject().put("en", message))
                put("data", JSONObject().apply {
                    put("type", "custom_message")
                    put("restaurantName", restaurantName)
                })
            }

            sendNotificationRequest(payload, "custom message to ${playerIds.size} users")
            return true
        } catch (e: Exception) {
            Log.e("OneSignalPartner", "Error sending custom notification: ${e.message}", e)
            return false
        }
    }

    /**
     * Send notification to multiple users about their order status (bulk accept/reject)
     */
    suspend fun sendBulkOrderStatusNotification(
        orderIds: List<String>,
        newStatus: String,
        restaurantName: String
    ) {
        Log.d("OneSignalPartner", "Sending bulk $newStatus notifications for ${orderIds.size} orders")

        try {
            val playerIdsMap = mutableMapOf<String, String>() // playerId to orderId mapping

            // Fetch order details and player IDs
            for (orderId in orderIds) {
                try {
                    val orderDoc = Firebase.firestore.collection("orders").document(orderId)
                        .get().await()
                    val userId = orderDoc.getString("userId")

                    if (userId != null) {
                        val userDoc = Firebase.firestore.collection("users").document(userId)
                            .get().await()
                        val playerId = userDoc.getString("oneSignalPlayerId")

                        if (playerId != null) {
                            playerIdsMap[playerId] = orderId
                        } else {
                            Log.w("OneSignalPartner", "No playerId for userId: $userId (order: $orderId)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OneSignalPartner", "Error fetching data for order $orderId: ${e.message}")
                }
            }

            if (playerIdsMap.isEmpty()) {
                Log.w("OneSignalPartner", "No valid playerIds found for bulk notification")
                return
            }

            val headingText: String
            val contentText: String
            when (newStatus) {
                "Accepted" -> {
                    headingText = "Order Accepted! 🎉"
                    contentText = "Great news! $restaurantName has accepted your order.\nYour food will be ready soon! 😋"
                }
                "Rejected" -> {
                    headingText = "Order Update ⚠️"
                    contentText = "Sorry, $restaurantName couldn't accept your order right now.\nPlease try ordering from another restaurant."
                }
                else -> {
                    headingText = "Order Status Update"
                    contentText = "Your order from $restaurantName is now $newStatus."
                }
            }

            val payload = JSONObject().apply {
                put("app_id", ONE_SIGNAL_APP_ID)
                put("include_player_ids", JSONArray().apply {
                    playerIdsMap.keys.forEach { put(it) }
                })
                put("headings", JSONObject().put("en", headingText))
                put("contents", JSONObject().put("en", contentText))
                put("data", JSONObject().apply {
                    put("status", newStatus)
                    put("type", "bulk_order_update")
                })
            }

            sendNotificationRequest(payload, "bulk ${newStatus.lowercase()} to ${playerIdsMap.size} users")
        } catch (e: Exception) {
            Log.e("OneSignalPartner", "Error sending bulk notification: ${e.message}", e)
        }
    }

    /**
     * Helper function to send the actual HTTP request
     */
    private suspend fun sendNotificationRequest(payload: JSONObject, logContext: String) {
        Log.d("OneSignalPartner", "Sending notification for $logContext")
        Log.d("OneSignalPartner", "Payload: $payload")

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ONE_SIGNAL_API_URL)
            .addHeader("Authorization", "Basic $ONE_SIGNAL_REST_API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: "No response body"
                    Log.d("OneSignalPartner", "API Response for $logContext: Code ${response.code}, Body $responseBody")

                    if (response.isSuccessful) {
                        Log.d("OneSignalPartner", "Notification sent successfully for $logContext")
                    } else {
                        Log.e("OneSignalPartner", "Failed to send notification for $logContext: Code ${response.code}, Body $responseBody")
                    }
                }
            } catch (e: Exception) {
                Log.e("OneSignalPartner", "Network error for $logContext: ${e.message}", e)
            }
        }
    }
}