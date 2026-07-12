package com.eventflow.modules.ordering.application.command;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(UUID buyerId, UUID idempotencyKey, List<Item> items) {

    public record Item(String type, UUID referenceId, int quantity) {
    }
}
