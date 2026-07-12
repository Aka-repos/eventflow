package com.eventflow.modules.ordering.api;

import com.eventflow.modules.ordering.api.dto.OrderDtos.CreateOrderRequest;
import com.eventflow.modules.ordering.api.dto.OrderDtos.OrderResponse;
import com.eventflow.modules.ordering.api.dto.OrderDtos.PayOrderRequest;
import com.eventflow.modules.ordering.application.CancelOrderUseCase;
import com.eventflow.modules.ordering.application.CreateOrderUseCase;
import com.eventflow.modules.ordering.application.GetOrderUseCase;
import com.eventflow.modules.ordering.application.ListOrdersUseCase;
import com.eventflow.modules.ordering.application.PayOrderUseCase;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.shared.idempotency.IdempotencyService;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.DataResponse;
import com.eventflow.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Tag orders: createOrder ⚡, payOrder ⚡, cancelOrder, listOrders, getOrder. */
@RestController
@RequestMapping("/orders")
class OrderController {

    private final CreateOrderUseCase createOrder;
    private final PayOrderUseCase payOrder;
    private final CancelOrderUseCase cancelOrder;
    private final ListOrdersUseCase listOrders;
    private final GetOrderUseCase getOrder;
    private final IdempotencyService idempotency;
    private final OrderApiMapper mapper;

    OrderController(CreateOrderUseCase createOrder, PayOrderUseCase payOrder, CancelOrderUseCase cancelOrder,
                    ListOrdersUseCase listOrders, GetOrderUseCase getOrder, IdempotencyService idempotency,
                    OrderApiMapper mapper) {
        this.createOrder = createOrder;
        this.payOrder = payOrder;
        this.cancelOrder = cancelOrder;
        this.listOrders = listOrders;
        this.getOrder = getOrder;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    @PostMapping
    ResponseEntity<DataResponse<OrderResponse>> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse response = idempotency.execute(user.id(), idempotencyKey, "createOrder", request,
                OrderResponse.class, () -> {
                    OrderResult result = createOrder.execute(new CreateOrderCommand(user.id(), idempotencyKey,
                            request.items().stream().map(i -> new CreateOrderCommand.Item(
                                    i.type(), i.referenceId(), i.quantity())).toList()));
                    return mapper.toResponse(result);
                });
        return ResponseEntity.created(URI.create("/orders/" + response.id()))
                .body(DataResponse.of(response));
    }

    @PostMapping("/{orderId}/pay")
    DataResponse<OrderResponse> payOrder(@PathVariable UUID orderId,
                                         @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
                                         @Valid @RequestBody PayOrderRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse response = idempotency.execute(user.id(), idempotencyKey,
                "payOrder:" + orderId, request, OrderResponse.class,
                () -> mapper.toResponse(payOrder.execute(user.id(), orderId, request.method())));
        return DataResponse.of(response);
    }

    @PostMapping("/{orderId}/cancel")
    DataResponse<OrderResponse> cancelOrder(@PathVariable UUID orderId,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(mapper.toResponse(cancelOrder.execute(user.id(), orderId)));
    }

    @GetMapping
    PageResponse<OrderResponse> listOrders(@RequestParam(required = false) OrderStatus status,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false, defaultValue = "20") int limit,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        CursorPage<OrderResult> page = listOrders.execute(user.id(), status, cursor, boundedLimit);
        return PageResponse.of(page.items().stream().map(mapper::toResponse).toList(), page);
    }

    @GetMapping("/{orderId}")
    DataResponse<OrderResponse> getOrder(@PathVariable UUID orderId,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(mapper.toResponse(getOrder.execute(user.id(), orderId)));
    }
}
