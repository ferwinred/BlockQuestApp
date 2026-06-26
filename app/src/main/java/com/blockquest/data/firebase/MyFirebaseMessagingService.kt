package com.blockquest.data.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM Token refreshed: $token")
        // If we had a backend, we'd send the token there.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM Message received from: ${message.from}")

        // Check if message contains a notification payload.
        message.notification?.let {
            Timber.d("Message Notification Body: ${it.body}")
        }

        // Handle data payload if any
        if (message.data.isNotEmpty()) {
            Timber.d("Message data payload: ${message.data}")
        }
    }
}
