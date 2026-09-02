package com.ticketingplatform.backend.notifications

interface NotificationSender {
    fun send(notification: ClaimedNotification)
}

open class NotificationDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class TerminalNotificationDeliveryException(message: String, cause: Throwable? = null) : NotificationDeliveryException(message, cause)
