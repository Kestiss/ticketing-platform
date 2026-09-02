package com.ticketingplatform.backend.notifications

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class LocalDevelopmentEmailProviderAdapter : EmailProviderAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(request: EmailDeliveryRequest) {
        logger.info(
            "notification dispatched: id={}, template={}, recipient={}, metadata={}",
            request.notificationId,
            request.template,
            request.recipientEmail,
            request.metadata,
        )
    }
}
