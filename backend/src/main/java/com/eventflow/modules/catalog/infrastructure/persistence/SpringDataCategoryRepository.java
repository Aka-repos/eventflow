package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCategoryRepository extends JpaRepository<Category, Short> {

    List<Category> findByActiveTrueOrderByName();
}
