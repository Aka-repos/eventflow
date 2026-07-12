package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    public ListCategoriesUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> execute() {
        return categoryRepository.findActive();
    }
}
