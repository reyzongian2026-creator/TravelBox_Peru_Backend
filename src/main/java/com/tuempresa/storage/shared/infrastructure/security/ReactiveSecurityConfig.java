package com.tuempresa.storage.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.infrastructure.web.ApiErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveSecurityConfig {

    private final ReactiveFirebaseAuthenticationManager reactiveFirebaseAuthenticationManager;
    private final FirebaseBearerTokenServerAuthenticationConverter tokenAuthenticationConverter;
    private final ReactiveAuthRateLimitWebFilter reactiveAuthRateLimitWebFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public ReactiveSecurityConfig(
            ReactiveFirebaseAuthenticationManager reactiveFirebaseAuthenticationManager,
            FirebaseBearerTokenServerAuthenticationConverter tokenAuthenticationConverter,
            ReactiveAuthRateLimitWebFilter reactiveAuthRateLimitWebFilter,
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        this.reactiveFirebaseAuthenticationManager = reactiveFirebaseAuthenticationManager;
        this.tokenAuthenticationConverter = tokenAuthenticationConverter;
        this.reactiveAuthRateLimitWebFilter = reactiveAuthRateLimitWebFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    @Bean
    public SecurityWebFilterChain reactiveSecurityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(reactiveFirebaseAuthenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(tokenAuthenticationConverter);
        authenticationWebFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> writeJsonError(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "AUTH_REQUIRED",
                                "Debes iniciar sesion para acceder a este recurso."
                        ))
                        .accessDeniedHandler((exchange, ex) -> writeJsonError(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "No tienes permisos para esta operacion."
                        ))
                )
                .authorizeExchange(auth -> auth
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/api/v1/payments/webhooks/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers(HttpMethod.GET, "/images/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/reservations/*/qr").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/files/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/geo/**", "/api/v1/warehouses/**").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(reactiveAuthRateLimitWebFilter, SecurityWebFiltersOrder.FIRST)
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Correlation-Id"
        ));
        config.setExposedHeaders(List.of("Authorization", "X-Correlation-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private Mono<Void> writeJsonError(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                exchange.getRequest().getPath().value(),
                List.of()
        );
        try {
            byte[] payload = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(payload)));
        } catch (Exception ex) {
            return exchange.getResponse().setComplete();
        }
    }
}
