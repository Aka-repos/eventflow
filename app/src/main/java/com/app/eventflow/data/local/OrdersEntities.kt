package com.app.eventflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Orden cacheada para lectura offline (api/09 §3). El JSON de ítems/pago se guarda serializado. */
@Entity(tableName = "order_cache")
data class OrderEntity(
    @PrimaryKey val id: String,
    val status: String,
    val totalAmount: String,
    val totalCurrency: String,
    val expiresAt: String,
    val createdAt: String,
    val itemsJson: String,
    val paymentJson: String?,
)

/** Boleto cacheado para lectura offline. */
@Entity(tableName = "ticket_cache")
data class TicketEntity(
    @PrimaryKey val id: String,
    val eventId: String?,
    val eventTitle: String?,
    val eventVenue: String?,
    val eventStartsAt: String?,
    val eventTimezone: String?,
    val ticketTypeName: String,
    val zoneName: String?,
    val status: String,
    val acquiredVia: String,
    val purchasedAt: String,
    val qrAvailableAt: String?,
    val canRecover: Boolean,
)
