package uz.sbg.marking.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@Component
public class ApiRoleGuardFilter extends OncePerRequestFilter {
    public static final String HEADER_ROLE = "X-SBG-Role";
    public static final String HEADER_TOKEN = "X-SBG-Token";

    @Value("${sbg.marking.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${sbg.marking.auth.admin-token:}")
    private String adminToken;

    @Value("${sbg.marking.auth.operator-token:}")
    private String operatorToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        RoleRequirement required = resolveRequirement(request.getRequestURI());
        if (required == RoleRequirement.NONE) {
            filterChain.doFilter(request, response);
            return;
        }

        String role = normalizeRole(request.getHeader(HEADER_ROLE));
        String token = request.getHeader(HEADER_TOKEN);

        boolean adminAuthorized = "ADMIN".equals(role) && tokenMatches(token, adminToken);
        boolean operatorAuthorized = "OPERATOR".equals(role) && tokenMatches(token, operatorToken);

        boolean authorized = required == RoleRequirement.ADMIN
                ? adminAuthorized
                : (adminAuthorized || operatorAuthorized);

        if (!authorized) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid role/token headers.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RoleRequirement resolveRequirement(String path) {
        if (path == null || !path.startsWith("/api/v1/")) {
            return RoleRequirement.NONE;
        }

        if (path.startsWith("/api/v1/admin/") || path.equals("/api/v1/validation/policy") || path.startsWith("/api/v1/km/import/")) {
            return RoleRequirement.ADMIN;
        }

        if (path.startsWith("/api/v1/marking/")
                || path.equals("/api/v1/validation/check")
                || path.startsWith("/api/v1/reports/")
                || path.startsWith("/api/v1/km/debug/")) {
            return RoleRequirement.OPERATOR;
        }

        return RoleRequirement.NONE;
    }

    private boolean tokenMatches(String token, String expectedToken) {
        if (isBlank(expectedToken)) {
            return false;
        }
        return Objects.equals(token, expectedToken);
    }

    private String normalizeRole(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum RoleRequirement {
        NONE,
        OPERATOR,
        ADMIN
    }
}
