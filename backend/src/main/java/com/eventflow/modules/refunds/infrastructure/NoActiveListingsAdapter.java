package com.eventflow.modules.refunds.infrastructure;

import com.eventflow.modules.refunds.domain.port.ListingsReadPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementación de preparación (MEJORA autorizada): hoy no existe el Exchange, así que jamás hay
 * un listing activo. El Módulo 6 sustituirá este adapter por la lectura real de commerce.exchange_listings
 * SIN tocar el flujo de reembolsos — el doble candado ya está cableado en RequestRefundUseCase.
 */
@Component
class NoActiveListingsAdapter implements ListingsReadPort {

    @Override
    public boolean hasActiveListing(UUID ticketId) {
        return false;
    }
}
