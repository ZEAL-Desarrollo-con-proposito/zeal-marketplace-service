package co.zeal.marketplace.chat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    static final String PRINCIPAL_ATTRIBUTE = ChatPrincipal.class.getName();
    private static final String TOKEN_QUERY_PARAMETER = "token";

    @Value("${app.jwt.secret}")
    private String secret;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String authorization = request.getHeaders().getFirst("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : UriComponentsBuilder.fromUri(request.getURI()).build()
                        .getQueryParams().getFirst(TOKEN_QUERY_PARAMETER);

        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            if (userId == null || userId.isBlank()
                    || !("EMPLOYEE".equals(role) || "PSYCHOLOGIST".equals(role))) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(PRINCIPAL_ATTRIBUTE, new ChatPrincipal(userId, role));
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
