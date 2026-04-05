package com.tuempresa.storage.shared.realtime;

import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.security.SseTokenStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeTokenController {

    private final SseTokenStore sseTokenStore;
    private final SecurityUtils securityUtils;

    public RealtimeTokenController(SseTokenStore sseTokenStore, SecurityUtils securityUtils) {
        this.sseTokenStore = sseTokenStore;
        this.securityUtils = securityUtils;
    }

    /**
     * Issues a short-lived opaque token for the WebSocket handshake,
     * so the JWT is never exposed in the URL query string.
     */
    @PostMapping("/ws-token")
    public Mono<Map<String, String>> issueWsToken() {
        return securityUtils.currentUserOrThrowReactive()
                .map(currentUser -> Map.of("token",
                        sseTokenStore.issueToken(currentUser.getId().toString())));
    }
}
