package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.exception.CategoryNameTakenException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class JpaCategoryRepositoryAdapter implements CategoryRepository {

    private final SpringDataCategoryRepository jpa;

    JpaCategoryRepositoryAdapter(SpringDataCategoryRepository jpa) {
        this.jpa = jpa;
    }

    /** api/02 §4.5: la violación del unique de nombre se traduce a conflicto, nunca a 500. */
    @Override
    public Category save(Category category) {
        try {
            return jpa.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryNameTakenException();
        }
    }

    @Override
    public Optional<Category> findById(short id) {
        return jpa.findById(id);
    }

    @Override
    public List<Category> findActive() {
        return jpa.findByActiveTrueOrderByName();
    }

    @Override
    public void delete(Category category) {
        jpa.delete(category);
        jpa.flush();
    }
}
