package com.eventflow.modules.checkin.application.result;

/** Resultado del check-in para la respuesta (GRANTED con datos del asistente). */
public record CheckInResultView(String attendeeName, String ticketTypeName, String zoneName,
                                java.time.Instant occurredAt) {
}
