package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(short id);

    List<Category> findActive();

    void delete(Category category);
}
