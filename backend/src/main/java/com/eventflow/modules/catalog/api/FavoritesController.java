package com.eventflow.modules.catalog.api;

import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventSummaryDto;
import com.eventflow.modules.catalog.application.AddFavoriteUseCase;
import com.eventflow.modules.catalog.application.ListFavoritesUseCase;
import com.eventflow.modules.catalog.application.RemoveFavoriteUseCase;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Tag me: listFavorites, addFavorite, removeFavorite (idempotentes; matriz offline doc api/09). */
@RestController
class FavoritesController {

    private final ListFavoritesUseCase listFavorites;
    private final AddFavoriteUseCase addFavorite;
    private final RemoveFavoriteUseCase removeFavorite;
    private final CatalogApiMapper mapper;

    FavoritesController(ListFavoritesUseCase listFavorites, AddFavoriteUseCase addFavorite,
                        RemoveFavoriteUseCase removeFavorite, CatalogApiMapper mapper) {
        this.listFavorites = listFavorites;
        this.addFavorite = addFavorite;
        this.removeFavorite = removeFavorite;
        this.mapper = mapper;
    }

    @GetMapping("/me/favorites")
    DataResponse<List<EventSummaryDto>> listFavorites(@AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(listFavorites.execute(user.id()).stream().map(mapper::toSummary).toList());
    }

    @PutMapping("/me/favorites/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addFavorite(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser user) {
        addFavorite.execute(user.id(), eventId);
    }

    @DeleteMapping("/me/favorites/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeFavorite(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser user) {
        removeFavorite.execute(user.id(), eventId);
    }
}
