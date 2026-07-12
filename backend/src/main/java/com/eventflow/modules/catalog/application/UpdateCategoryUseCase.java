package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.exception.CategoryNotFoundException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category execute(short id, String name, String icon, boolean active) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryNotFoundException::new);
        category.rename(name, icon, active);
        return categoryRepository.save(category);
    }
}
