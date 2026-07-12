package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataEventRepository extends JpaRepository<Event, UUID> {

    boolean existsByCategoryId(short categoryId);
}
