package com.ticketingplatform.backend.notifications

interface EmailProviderAdapter {
    fun send(request: EmailDeliveryRequest)
}

data class EmailDeliveryRequest(
    val notificationId: String,
    val recipientEmail: String,
    val template: String,
    val metadata: Map<String, String>,
    val attributes: Map<String, Any>,
)
