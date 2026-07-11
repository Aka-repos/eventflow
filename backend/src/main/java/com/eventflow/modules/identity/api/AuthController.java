package com.eventflow.modules.identity.api;

import com.eventflow.modules.identity.api.dto.AuthDtos.AuthTokensResponse;
import com.eventflow.modules.identity.api.dto.AuthDtos.LoginRequest;
import com.eventflow.modules.identity.api.dto.AuthDtos.LogoutRequest;
import com.eventflow.modules.identity.api.dto.AuthDtos.RefreshRequest;
import com.eventflow.modules.identity.api.dto.AuthDtos.RegisterRequest;
import com.eventflow.modules.identity.application.LoginUseCase;
import com.eventflow.modules.identity.application.LogoutUseCase;
import com.eventflow.modules.identity.application.RefreshTokenUseCase;
import com.eventflow.modules.identity.application.RegisterUserUseCase;
import com.eventflow.modules.identity.application.command.LoginCommand;
import com.eventflow.modules.identity.application.command.LogoutCommand;
import com.eventflow.modules.identity.application.command.RefreshCommand;
import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@Tag(name = "auth")
class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final AuthApiMapper mapper;

    AuthController(RegisterUserUseCase registerUserUseCase, LoginUseCase loginUseCase,
                   RefreshTokenUseCase refreshTokenUseCase, LogoutUseCase logoutUseCase, AuthApiMapper mapper) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    @Operation(operationId = "register", summary = "Registro de asistente")
    ResponseEntity<DataResponse<AuthTokensResponse>> register(@Valid @RequestBody RegisterRequest request) {
        var result = registerUserUseCase.execute(new RegisterUserCommand(
                request.email(), request.password(), request.fullName(), request.phone()));
        return ResponseEntity.created(URI.create("/api/v1/me"))
                .body(DataResponse.of(mapper.toResponse(result)));
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Inicio de sesión")
    DataResponse<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        return DataResponse.of(mapper.toResponse(result));
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refreshToken", summary = "Rotación de refresh token")
    DataResponse<AuthTokensResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        var result = refreshTokenUseCase.execute(new RefreshCommand(request.refreshToken()));
        return DataResponse.of(mapper.toResponse(result));
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "Revoca el refresh token")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request,
                                @AuthenticationPrincipal AuthenticatedUser principal) {
        logoutUseCase.execute(new LogoutCommand(principal.id(), request.refreshToken()));
        return ResponseEntity.noContent().build();
    }
}
