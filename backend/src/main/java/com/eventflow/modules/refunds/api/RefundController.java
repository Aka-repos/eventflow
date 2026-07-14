package com.eventflow.modules.refunds.api;

import com.eventflow.modules.refunds.api.dto.RefundDtos.CreateRefundRequest;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RecoveryOptionsResponse;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RefundResponse;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RejectRefundRequest;
import com.eventflow.modules.refunds.application.ApproveRefundUseCase;
import com.eventflow.modules.refunds.application.GetRecoveryOptionsUseCase;
import com.eventflow.modules.refunds.application.ListEventRefundsUseCase;
import com.eventflow.modules.refunds.application.RejectRefundUseCase;
import com.eventflow.modules.refunds.application.RequestRefundUseCase;
import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.refunds.domain.RefundStatus;
import com.eventflow.shared.idempotency.IdempotencyService;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.DataResponse;
import com.eventflow.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Reembolsos (S3). Solicitud por el asistente (tag tickets); aprobación/rechazo/listado por el
 * organizador dueño del evento (tag organizer). recovery-options vive en TicketController (tag tickets).
 */
@RestController
class RefundController {

    private final GetRecoveryOptionsUseCase getRecoveryOptions;
    private final RequestRefundUseCase requestRefund;
    private final ApproveRefundUseCase approveRefund;
    private final RejectRefundUseCase rejectRefund;
    private final ListEventRefundsUseCase listEventRefunds;
    private final IdempotencyService idempotency;
    private final RefundApiMapper mapper;

    RefundController(GetRecoveryOptionsUseCase getRecoveryOptions, RequestRefundUseCase requestRefund,
                     ApproveRefundUseCase approveRefund, RejectRefundUseCase rejectRefund,
                     ListEventRefundsUseCase listEventRefunds, IdempotencyService idempotency,
                     RefundApiMapper mapper) {
        this.getRecoveryOptions = getRecoveryOptions;
        this.requestRefund = requestRefund;
        this.approveRefund = approveRefund;
        this.rejectRefund = rejectRefund;
        this.listEventRefunds = listEventRefunds;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    @GetMapping("/tickets/{ticketId}/recovery-options")
    DataResponse<RecoveryOptionsResponse> getRecoveryOptions(@PathVariable UUID ticketId,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(mapper.toRecovery(getRecoveryOptions.execute(user.id(), ticketId)));
    }

    @PostMapping("/tickets/{ticketId}/refund-requests")
    ResponseEntity<DataResponse<RefundResponse>> requestRefund(
            @PathVariable UUID ticketId,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
            @Valid @RequestBody(required = false) CreateRefundRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String reason = request == null ? null : request.reason();
        RefundResponse response = idempotency.execute(user.id(), key, "requestRefund:" + ticketId,
                request, RefundResponse.class,
                () -> mapper.toResponse(requestRefund.execute(user.id(), ticketId, reason)));
        return ResponseEntity.created(URI.create("/refund-requests/" + response.id()))
                .body(DataResponse.of(response));
    }

    @PostMapping("/refund-requests/{refundId}/approve")
    @PreAuthorize("hasRole('ORGANIZER')")
    DataResponse<RefundResponse> approveRefund(@PathVariable UUID refundId,
                                               @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        RefundResponse response = idempotency.execute(user.id(), key, "approveRefund:" + refundId, null,
                RefundResponse.class, () -> mapper.toResponse(approveRefund.execute(user.id(), refundId)));
        return DataResponse.of(response);
    }

    @PostMapping("/refund-requests/{refundId}/reject")
    @PreAuthorize("hasRole('ORGANIZER')")
    DataResponse<RefundResponse> rejectRefund(@PathVariable UUID refundId,
                                              @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
                                              @Valid @RequestBody RejectRefundRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        RefundResponse response = idempotency.execute(user.id(), key, "rejectRefund:" + refundId, request,
                RefundResponse.class,
                () -> mapper.toResponse(rejectRefund.execute(user.id(), refundId, request.reason())));
        return DataResponse.of(response);
    }

    @GetMapping("/organizer/events/{eventId}/refund-requests")
    @PreAuthorize("hasRole('ORGANIZER')")
    PageResponse<RefundResponse> listEventRefunds(@PathVariable UUID eventId,
                                                  @RequestParam(required = false) RefundStatus status,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false, defaultValue = "20") int limit,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        CursorPage<RefundRequest> page = listEventRefunds.execute(user.id(), eventId, status, cursor, boundedLimit);
        return PageResponse.of(page.items().stream().map(mapper::toResponse).toList(), page);
    }
}
