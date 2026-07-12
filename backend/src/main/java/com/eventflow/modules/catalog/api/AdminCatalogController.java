package com.eventflow.modules.catalog.api;

import com.eventflow.modules.catalog.api.dto.CatalogDtos.CategoryDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.CategoryRequest;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.SponsorDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.SponsorRequest;
import com.eventflow.modules.catalog.application.CreateCategoryUseCase;
import com.eventflow.modules.catalog.application.CreateSponsorUseCase;
import com.eventflow.modules.catalog.application.DeleteCategoryUseCase;
import com.eventflow.modules.catalog.application.DeleteSponsorUseCase;
import com.eventflow.modules.catalog.application.UpdateCategoryUseCase;
import com.eventflow.modules.catalog.application.UpdateSponsorUseCase;
import com.eventflow.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tag admin (categorías y patrocinadores del catálogo). */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminCatalogController {

    private final CreateCategoryUseCase createCategory;
    private final UpdateCategoryUseCase updateCategory;
    private final DeleteCategoryUseCase deleteCategory;
    private final CreateSponsorUseCase createSponsor;
    private final UpdateSponsorUseCase updateSponsor;
    private final DeleteSponsorUseCase deleteSponsor;
    private final CatalogApiMapper mapper;

    AdminCatalogController(CreateCategoryUseCase createCategory, UpdateCategoryUseCase updateCategory,
                           DeleteCategoryUseCase deleteCategory, CreateSponsorUseCase createSponsor,
                           UpdateSponsorUseCase updateSponsor, DeleteSponsorUseCase deleteSponsor,
                           CatalogApiMapper mapper) {
        this.createCategory = createCategory;
        this.updateCategory = updateCategory;
        this.deleteCategory = deleteCategory;
        this.createSponsor = createSponsor;
        this.updateSponsor = updateSponsor;
        this.deleteSponsor = deleteSponsor;
        this.mapper = mapper;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    DataResponse<CategoryDto> createCategory(@Valid @RequestBody CategoryRequest request) {
        return DataResponse.of(mapper.toCategoryDto(
                createCategory.execute(request.name(), request.icon(), request.active())));
    }

    @PatchMapping("/categories/{id}")
    DataResponse<CategoryDto> updateCategory(@PathVariable short id, @Valid @RequestBody CategoryRequest request) {
        return DataResponse.of(mapper.toCategoryDto(
                updateCategory.execute(id, request.name(), request.icon(), request.active())));
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable short id) {
        deleteCategory.execute(id);
    }

    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    DataResponse<SponsorDto> createSponsor(@Valid @RequestBody SponsorRequest request) {
        return DataResponse.of(mapper.toSponsorDto(
                createSponsor.execute(request.name(), request.logoUrl(), request.website())));
    }

    @PatchMapping("/sponsors/{id}")
    DataResponse<SponsorDto> updateSponsor(@PathVariable UUID id, @Valid @RequestBody SponsorRequest request) {
        return DataResponse.of(mapper.toSponsorDto(
                updateSponsor.execute(id, request.name(), request.logoUrl(), request.website())));
    }

    @DeleteMapping("/sponsors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSponsor(@PathVariable UUID id) {
        deleteSponsor.execute(id);
    }
}
