package com.tuempresa.storage.shared.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveCorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());
        exchange.getResponse().getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
        exchange.getAttributes().put(CorrelationIdFilter.CORRELATION_ID, correlationId);
        return chain.filter(exchange);
    }
}
