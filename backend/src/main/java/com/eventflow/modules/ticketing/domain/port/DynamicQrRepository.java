package com.eventflow.modules.ticketing.domain.port;

import com.eventflow.modules.ticketing.domain.DynamicQr;

import java.util.Optional;
import java.util.UUID;

public interface DynamicQrRepository {

    DynamicQr save(DynamicQr qr);

    Optional<DynamicQr> findById(UUID id);

    /** QR vivo (ACTIVE/BLOCKED) del boleto, si existe — el índice único garantiza a lo sumo uno. */
    Optional<DynamicQr> findLiveByTicketId(UUID ticketId);

    /** Bajo lock de fila para el check-in (serializa consumo concurrente del mismo QR). */
    Optional<DynamicQr> findByIdForUpdate(UUID id);
}
