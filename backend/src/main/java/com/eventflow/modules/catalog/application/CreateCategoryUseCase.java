package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public CreateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** El adapter traduce la violación del unique de nombre a category_name_taken (409). */
    @Transactional
    public Category execute(String name, String icon, boolean active) {
        return categoryRepository.save(Category.create(name, icon, active));
    }
}
