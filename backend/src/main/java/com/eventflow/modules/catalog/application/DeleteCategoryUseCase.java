package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.exception.CategoryInUseException;
import com.eventflow.modules.catalog.domain.exception.CategoryNotFoundException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    public DeleteCategoryUseCase(CategoryRepository categoryRepository, EventRepository eventRepository) {
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void execute(short id) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryNotFoundException::new);
        if (eventRepository.existsByCategoryId(id)) {
            throw new CategoryInUseException();
        }
        categoryRepository.delete(category);
    }
}
