package local.mutualfund.screener.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import local.mutualfund.screener.dto.AdminDtos.EnabledRequest;
import local.mutualfund.screener.dto.AdminDtos.PermissionOverrideRequest;
import local.mutualfund.screener.dto.AdminDtos.PermissionSummary;
import local.mutualfund.screener.dto.AdminDtos.RolesRequest;
import local.mutualfund.screener.dto.AdminDtos.UserOverridableRequest;
import local.mutualfund.screener.dto.AdminDtos.UserPermissionSummary;
import local.mutualfund.screener.dto.AdminDtos.UserSummary;
import local.mutualfund.screener.dto.AuthDtos.RegisterRequest;
import local.mutualfund.screener.dto.AuthDtos.LoginAuditResponse;
import local.mutualfund.screener.dto.AuthDtos.UserResponse;
import local.mutualfund.screener.security.AppUserPrincipal;
import local.mutualfund.screener.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'USER_MANAGE')")
    public List<UserSummary> getUsers() {
        return adminService.getUsers();
    }

    @GetMapping("/login-audit")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'USER_MANAGE')")
    public List<LoginAuditResponse> getLoginAudit() {
        return adminService.getLoginAudit();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'USER_MANAGE')")
    public UserResponse createUser(
            @Valid @RequestBody RegisterRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        return adminService.createUser(request, actor);
    }

    @PatchMapping("/users/{userId}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'USER_MANAGE')")
    public void setUserEnabled(
            @PathVariable @jakarta.validation.constraints.Positive long userId,
            @Valid @RequestBody EnabledRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        adminService.setEnabled(userId, request.enabled(), actor);
    }

    @PutMapping("/users/{userId}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'USER_MANAGE')")
    public void replaceUserRoles(
            @PathVariable @jakarta.validation.constraints.Positive long userId,
            @Valid @RequestBody RolesRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        adminService.replaceRoles(userId, request.roles(), actor);
    }

    @GetMapping("/roles")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public List<AdminService.RoleSummary> getRoles() {
        return adminService.getRoles();
    }

    @PutMapping("/roles/{roleName}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public void replaceRolePermissions(
            @PathVariable @Pattern(regexp = "ADMIN|USER|admin|user") String roleName,
            @Valid @RequestBody RolesPermissionsRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        adminService.replaceRolePermissions(roleName, request.permissionCodes(), actor);
    }

    @GetMapping("/permissions")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public List<PermissionSummary> getPermissions() {
        return adminService.getPermissions();
    }

    @PutMapping("/permissions/{permissionCode}/user-overridable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public void setPermissionOverridable(
            @PathVariable @Pattern(regexp = "[A-Za-z_]+") String permissionCode,
            @Valid @RequestBody UserOverridableRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        adminService.setPermissionOverridable(
                permissionCode, request.userOverridable(), actor
        );
    }

    @GetMapping("/users/{userId}/permissions")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public List<UserPermissionSummary> getUserPermissionOverrides(
            @PathVariable @jakarta.validation.constraints.Positive long userId
    ) {
        return adminService.getUserPermissionOverrides(userId);
    }

    @PutMapping("/users/{userId}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public void setUserPermissionOverride(
            @PathVariable @jakarta.validation.constraints.Positive long userId,
            @PathVariable @Pattern(regexp = "[A-Za-z_]+") String permissionCode,
            @Valid @RequestBody PermissionOverrideRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        adminService.setUserPermissionOverride(
                userId, permissionCode, request.permissionValue(), actor
        );
    }

    @DeleteMapping("/users/{userId}/permissions/{permissionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'PERMISSION_MANAGE')")
    public void clearUserPermissionOverride(
            @PathVariable @jakarta.validation.constraints.Positive long userId,
            @PathVariable @Pattern(regexp = "[A-Za-z_]+") String permissionCode,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        adminService.clearUserPermissionOverride(userId, permissionCode, actor);
    }

    public record RolesPermissionsRequest(
            @jakarta.validation.constraints.NotNull List<@Pattern(regexp = "[A-Za-z_]+") String>
                    permissionCodes
    ) {
    }
}
