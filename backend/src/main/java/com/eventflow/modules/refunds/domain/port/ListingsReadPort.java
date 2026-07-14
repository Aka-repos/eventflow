package com.eventflow.modules.refunds.domain.port;

import java.util.UUID;

/**
 * Lectura del estado de publicación en el Exchange (MEJORA autorizada, preparación M6). Hoy siempre
 * devuelve "sin listing activo" — el doble candado refund↔listing (design/00 §7) queda cableado en
 * la capa de aplicación; el Módulo 6 implementará la lectura real sin tocar el flujo de reembolsos.
 */
public interface ListingsReadPort {

    boolean hasActiveListing(UUID ticketId);
}
