package com.coachsim.realtime;

import com.coachsim.auth.AuthPrincipal;
import com.coachsim.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over WebSocket:
 *  - /ws            -> SockJS-compatible endpoint
 *  - /topic/match.{id}  -> per-match broadcast (ball events, decision windows)
 *  - /user/queue/decision-result  -> per-user score reveal
 *
 * Currently uses a SimpleBroker (in-memory). To scale beyond one node,
 * swap `enableSimpleBroker` for `enableStompBrokerRelay` (RabbitMQ STOMP)
 * without changing any client code.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwt;

    public WebSocketConfig(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                    return message;
                }
                String auth = accessor.getFirstNativeHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    try {
                        Claims claims = jwt.parse(auth.substring(7));
                        AuthPrincipal p = new AuthPrincipal(
                                Long.parseLong(claims.getSubject()),
                                claims.get("email", String.class),
                                claims.get("role", String.class));
                        UsernamePasswordAuthenticationToken token =
                                new UsernamePasswordAuthenticationToken(
                                        p, null,
                                        List.of(new SimpleGrantedAuthority(p.role())));
                        accessor.setUser(token);
                    } catch (Exception ignored) {
                        // unauthenticated WS still allowed for public topics
                    }
                }
                return message;
            }
        });
    }
}
