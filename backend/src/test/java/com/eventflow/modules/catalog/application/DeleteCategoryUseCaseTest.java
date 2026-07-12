package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.exception.CategoryInUseException;
import com.eventflow.modules.catalog.domain.exception.CategoryNotFoundException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryUseCaseTest {

    @Mock CategoryRepository categoryRepository;
    @Mock EventRepository eventRepository;
    @InjectMocks DeleteCategoryUseCase useCase;

    @Test
    void category_in_use_is_conflict() {
        Category category = Category.create("Conciertos", null, true);
        when(categoryRepository.findById((short) 1)).thenReturn(Optional.of(category));
        when(eventRepository.existsByCategoryId((short) 1)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute((short) 1)).isInstanceOf(CategoryInUseException.class);
        verify(categoryRepository, never()).delete(category);
    }

    @Test
    void unused_category_is_deleted() {
        Category category = Category.create("Teatro", null, true);
        when(categoryRepository.findById((short) 2)).thenReturn(Optional.of(category));
        when(eventRepository.existsByCategoryId((short) 2)).thenReturn(false);

        useCase.execute((short) 2);

        verify(categoryRepository).delete(category);
    }

    @Test
    void missing_category_is_404() {
        when(categoryRepository.findById((short) 9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute((short) 9)).isInstanceOf(CategoryNotFoundException.class);
    }
}
