package local.mutualfund.screener.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    public record RolesRequest(
            @NotEmpty List<@Pattern(regexp = "ADMIN|USER") String> roles
    ) {
    }

    public record PermissionOverrideRequest(
            @NotNull @Pattern(regexp = "ALLOW|DENY") String permissionValue
    ) {
    }

    public record UserOverridableRequest(@NotNull Boolean userOverridable) {
    }

    public record UserSummary(
            @Positive long id,
            String username,
            String email,
            boolean enabled,
            boolean accountLocked,
            List<String> roles
    ) {
    }

    public record PermissionSummary(
            String code,
            String name,
            boolean userOverridable
    ) {
    }

    public record UserPermissionSummary(
            String permissionCode,
            String permissionValue
    ) {
    }
}
