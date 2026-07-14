package com.eventflow.modules.checkin.api;

import com.eventflow.modules.checkin.api.dto.StaffDtos.AssignStaffRequest;
import com.eventflow.modules.checkin.api.dto.StaffDtos.StaffAssignedResponse;
import com.eventflow.modules.checkin.application.AssignStaffUseCase;
import com.eventflow.modules.checkin.application.RemoveStaffUseCase;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Tag organizer: assignStaff, removeStaff (dueño del evento asigna staff de acceso). */
@RestController
@RequestMapping("/organizer/events/{eventId}/staff")
@PreAuthorize("hasRole('ORGANIZER')")
class StaffController {

    private final AssignStaffUseCase assignStaff;
    private final RemoveStaffUseCase removeStaff;

    StaffController(AssignStaffUseCase assignStaff, RemoveStaffUseCase removeStaff) {
        this.assignStaff = assignStaff;
        this.removeStaff = removeStaff;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DataResponse<StaffAssignedResponse> assign(@PathVariable UUID eventId,
                                               @Valid @RequestBody AssignStaffRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        UUID staffUserId = assignStaff.execute(user.id(), eventId, request.userEmail(),
                request.permissions() == null ? List.of() : request.permissions());
        return DataResponse.of(new StaffAssignedResponse(staffUserId.toString()));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID eventId, @PathVariable UUID userId,
                @AuthenticationPrincipal AuthenticatedUser user) {
        removeStaff.execute(user.id(), eventId, userId);
    }
}
