package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.common.enums.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header, validates the token,
 * checks the deny-list, and populates the security context with an
 * {@link AuthPrincipal}. Invalid/expired/revoked or absent tokens simply leave
 * the request unauthenticated; the entry point then returns 401 for protected
 * endpoints.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenyList tokenDenyList;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                String jti = claims.getId();
                if (jti == null || !tokenDenyList.isRevoked(jti)) {
                    authenticate(claims, jti);
                }
            } catch (Exception ex) {
                // Malformed/expired/invalid token: stay unauthenticated.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, String jti) {
        Long userId = ((Number) claims.get("uid")).longValue();
        Role role = Role.valueOf(claims.get("role", String.class));
        AuthPrincipal principal = new AuthPrincipal(userId, claims.getSubject(), role);

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                jti, // credentials carry the jti so logout can revoke this exact token
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
