package com.eventflow.modules.ordering.infrastructure.persistence;

import com.eventflow.modules.ordering.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataOrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> lockById(@Param("id") UUID id);
}
