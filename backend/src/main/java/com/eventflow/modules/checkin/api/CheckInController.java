package com.eventflow.modules.checkin.api;

import com.eventflow.modules.checkin.api.dto.CheckInDtos.CheckInRequest;
import com.eventflow.modules.checkin.api.dto.CheckInDtos.CheckInResponse;
import com.eventflow.modules.checkin.application.EventCheckInUseCase;
import com.eventflow.modules.checkin.application.result.CheckInResultView;
import com.eventflow.shared.idempotency.IdempotencyService;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tag checkin: eventCheckIn ⚡ (ORGANIZER dueño o STAFF asignado). El resultado GRANTED devuelve
 * 200; los rechazos son Problem RFC 9457 con su code (qr_invalid/expired, already_used, etc.).
 * La autorización fina (organizador vs staff) la resuelve el use case server-side.
 */
@RestController
@PreAuthorize("hasAnyRole('ORGANIZER','STAFF','ADMIN')")
class CheckInController {

    private final EventCheckInUseCase eventCheckIn;
    private final IdempotencyService idempotency;

    CheckInController(EventCheckInUseCase eventCheckIn, IdempotencyService idempotency) {
        this.eventCheckIn = eventCheckIn;
        this.idempotency = idempotency;
    }

    @PostMapping("/events/{eventId}/check-ins")
    DataResponse<CheckInResponse> eventCheckIn(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest http) {
        Map<String, Object> device = deviceInfo(http);
        CheckInResponse response = idempotency.execute(user.id(), idempotencyKey,
                "eventCheckIn:" + eventId, request, CheckInResponse.class, () -> {
                    CheckInResultView view = eventCheckIn.execute(user.id(), eventId, request.qrToken(), device);
                    return new CheckInResponse("GRANTED", view.attendeeName(), view.ticketTypeName(),
                            view.zoneName(), null, view.occurredAt());
                });
        return DataResponse.of(response);
    }

    private Map<String, Object> deviceInfo(HttpServletRequest http) {
        Map<String, Object> device = new HashMap<>();
        String ua = http.getHeader("User-Agent");
        if (ua != null) {
            device.put("userAgent", ua);
        }
        device.put("ip", http.getRemoteAddr());
        return device;
    }
}
