package com.digneequipe.hardoize.config;

import com.digneequipe.hardoize.websocket.GroupeWebSocketHandler;
import com.digneequipe.hardoize.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Remplace l'ancienne ébauche STOMP + SockJS (jamais utilisée) par un
 * WebSocket brut : plus simple et plus fiable avec le WebSocket natif
 * de React Native, qui ne gère pas bien SockJS.
 *
 * Point de connexion : wss://host/ws/groupe?token=...&groupeUuid=...
 * (voir JwtHandshakeInterceptor pour la validation avant upgrade).
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final GroupeWebSocketHandler groupeWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(groupeWebSocketHandler, "/ws/groupe")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
