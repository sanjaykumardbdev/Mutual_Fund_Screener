package local.mutualfund.screener.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import local.mutualfund.screener.dto.AuthDtos.CsrfResponse;
import local.mutualfund.screener.dto.AuthDtos.ChangePasswordRequest;
import local.mutualfund.screener.dto.AuthDtos.LoginRequest;
import local.mutualfund.screener.dto.AuthDtos.RegisterRequest;
import local.mutualfund.screener.dto.AuthDtos.UserResponse;
import local.mutualfund.screener.security.AppUserPrincipal;
import local.mutualfund.screener.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfTokenRepository;
    private final boolean registrationEnabled;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(
            AuthService authService,
            org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfTokenRepository,
            @Value("${app.registration.enabled:false}") boolean registrationEnabled
    ) {
        this.authService = authService;
        this.csrfTokenRepository = csrfTokenRepository;
        this.registrationEnabled = registrationEnabled;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        );
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return authService.login(requestBody, request, response);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest requestBody) {
        if (!registrationEnabled) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Registration is disabled"
            );
        }
        return authService.register(requestBody);
    }

    @GetMapping("/me")
    @PreAuthorize("@permissionService.isAccountActive(authentication)")
    public UserResponse currentUser(Authentication authentication) {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return authService.getUser(principal.getUserId());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionService.isAccountActive(authentication)")
    public void changePassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.getUserId(), request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        authService.logout(principal, request);
        csrfTokenRepository.saveToken(null, request, response);
        logoutHandler.logout(request, response, authentication);
        SecurityContextHolder.clearContext();
    }
}
