package com.eventflow.modules.checkin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** DTO espejo de AssignStaffRequest (OpenAPI congelado). */
public final class StaffDtos {

    private StaffDtos() {
    }

    public record AssignStaffRequest(@NotBlank @Email String userEmail, List<String> permissions) {
    }

    public record StaffAssignedResponse(String userId) {
    }
}
