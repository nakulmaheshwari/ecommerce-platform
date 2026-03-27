package com.ecommerce.identity.api;

import com.ecommerce.identity.api.dto.AssignRoleRequest;
import com.ecommerce.identity.api.dto.UpdateStatusRequest;
import com.ecommerce.identity.api.dto.UserResponse;
import com.ecommerce.identity.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateStatusRequest request) {
        adminService.updateUserStatus(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/roles")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID id, 
            @Valid @RequestBody AssignRoleRequest request) {
        adminService.assignRole(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
