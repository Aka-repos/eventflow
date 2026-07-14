package com.app.eventflow.domain.model.checkin

/** QR presentable del boleto (el cliente NO interpreta el token; solo lo pinta y sabe cuándo refrescar). */
data class TicketQr(
    val qrToken: String,
    val expiresAt: String,
    val refreshAfter: String,
)

/** Resultado del check-in visto por el escáner. GRANTED muestra datos; DENIED muestra el motivo. */
sealed interface CheckInOutcome {
    data class Granted(
        val attendeeName: String?,
        val ticketTypeName: String?,
        val zoneName: String?,
    ) : CheckInOutcome

    /** code enrutable (qr_invalid, qr_expired, already_used, checkin_wrong_event, ticket_blocked…). */
    data class Denied(val code: String, val message: String) : CheckInOutcome
}
