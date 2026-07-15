package com.eventflow.modules.identity.api;

import com.eventflow.modules.identity.api.dto.AuthDtos.UpdateProfileRequest;
import com.eventflow.modules.identity.api.dto.AuthDtos.UserProfileDto;
import com.eventflow.modules.identity.application.UpdateProfileUseCase;
import com.eventflow.modules.identity.application.command.UpdateProfileCommand;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil del usuario autenticado (tag me). El id sale del JWT (@AuthenticationPrincipal), nunca de la
 * ruta ni del cuerpo → imposible editar el perfil de otro. Espejo de la operación updateProfile.
 */
@RestController
@RequestMapping("/me")
@Tag(name = "me")
class MeController {

    private final UpdateProfileUseCase updateProfileUseCase;
    private final AuthApiMapper mapper;

    MeController(UpdateProfileUseCase updateProfileUseCase, AuthApiMapper mapper) {
        this.updateProfileUseCase = updateProfileUseCase;
        this.mapper = mapper;
    }

    @PutMapping
    @Operation(operationId = "updateProfile", summary = "Actualiza mi perfil")
    DataResponse<UserProfileDto> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        var updated = updateProfileUseCase.execute(
                new UpdateProfileCommand(user.id(), request.fullName(), request.phone()));
        return DataResponse.of(mapper.toProfile(updated));
    }
}
