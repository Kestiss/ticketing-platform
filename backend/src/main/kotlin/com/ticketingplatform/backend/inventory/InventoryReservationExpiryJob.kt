package com.ticketingplatform.backend.inventory

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class InventoryReservationExpiryJob(
    private val reservationService: InventoryReservationService,
) {
    @Scheduled(fixedDelayString = "${ticketing.inventory.expiry-interval-ms:30000}")
    fun expireDueReservations() {
        reservationService.expireDueReservations()
    }
}
