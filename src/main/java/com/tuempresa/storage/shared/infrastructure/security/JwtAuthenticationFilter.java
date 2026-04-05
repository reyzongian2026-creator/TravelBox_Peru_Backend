package com.tuempresa.storage.shared.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserDetailsService userDetailsService;
    private final SseTokenStore sseTokenStore;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AppUserDetailsService userDetailsService,
            SseTokenStore sseTokenStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.sseTokenStore = sseTokenStore;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // SSE endpoints: resolve short-lived opaque token instead of JWT
        if (isNotificationSseRequest(request)) {
            String sseToken = request.getParameter("accessToken");
            if (sseToken != null && !sseToken.isBlank()) {
                String username = sseTokenStore.resolveUsername(sseToken);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    AuthUserPrincipal principal = (AuthUserPrincipal) userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveBearerToken(request);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!jwtTokenProvider.isValid(token) || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String username = jwtTokenProvider.extractSubject(token);
        AuthUserPrincipal principal = (AuthUserPrincipal) userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean isNotificationSseRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/v1/notifications/events".equals(path)
                || "/api/v1/notifications/sse".equals(path);
    }
}
