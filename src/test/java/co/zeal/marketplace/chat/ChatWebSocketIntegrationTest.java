package co.zeal.marketplace.chat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import co.zeal.marketplace.config.SecurityFilter;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = ChatWebSocketIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!"
)
class ChatWebSocketIntegrationTest {
    private static final String SECRET =
            "ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!";

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({ChatWebSocketConfiguration.class, ChatHandshakeInterceptor.class,
            ChatInboundChannelInterceptor.class,
            ChatMessageController.class, ChatPresenceRegistry.class, SecurityFilter.class})
    static class TestApplication {}

    @LocalServerPort
    private int port;

    @Autowired
    private ChatPresenceRegistry presenceRegistry;

    private WebSocketStompClient client;

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.stop();
        }
    }

    @Test
    void employeesAndPsychologistsExchangeMessagesAndReconnect() throws Exception {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        TestSessionHandler employeeHandler = new TestSessionHandler();
        TestSessionHandler psychologistHandler = new TestSessionHandler();
        StompSession employee = connect("employee-1", "EMPLOYEE", employeeHandler);
        StompSession psychologist = connect("psychologist-1", "PSYCHOLOGIST", psychologistHandler);
        employee.subscribe("/user/queue/messages", employeeHandler);
        employee.subscribe("/user/queue/errors", employeeHandler);
        psychologist.subscribe("/user/queue/messages", psychologistHandler);
        psychologist.subscribe("/user/queue/errors", psychologistHandler);

        awaitConnected("employee-1");
        awaitConnected("psychologist-1");

        employee.send("/app/chat.send", Map.of(
                "recipientId", "psychologist-1", "content", "Necesito conversar."
        ));
        Map<String, Object> toPsychologist = psychologistHandler.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(toPsychologist);
        assertEquals("employee-1", toPsychologist.get("senderId"));
        assertEquals("psychologist-1", toPsychologist.get("recipientId"));
        assertEquals("Necesito conversar.", toPsychologist.get("content"));

        psychologist.send("/app/chat.send", Map.of(
                "recipientId", "employee-1", "content", "Estoy aquí para escucharte."
        ));
        Map<String, Object> toEmployee = employeeHandler.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(toEmployee);
        assertEquals("psychologist-1", toEmployee.get("senderId"));
        assertEquals("employee-1", toEmployee.get("recipientId"));

        psychologist.disconnect();
        awaitDisconnected("psychologist-1");
        employee.send("/app/chat.send", Map.of(
                "recipientId", "psychologist-1", "content", "Mensaje sin receptor."
        ));
        Map<String, Object> offlineError = employeeHandler.errors.poll(5, TimeUnit.SECONDS);
        assertNotNull(offlineError);
        assertEquals("El destinatario no está conectado.", offlineError.get("message"));

        TestSessionHandler reconnectedHandler = new TestSessionHandler();
        StompSession reconnectedPsychologist =
                connect("psychologist-1", "PSYCHOLOGIST", reconnectedHandler);
        reconnectedPsychologist.subscribe("/user/queue/messages", reconnectedHandler);
        awaitConnected("psychologist-1");

        employee.send("/app/chat.send", Map.of(
                "recipientId", "psychologist-1", "content", "Conexión restablecida."
        ));
        Map<String, Object> afterReconnect =
                reconnectedHandler.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(afterReconnect);
        assertEquals("Conexión restablecida.", afterReconnect.get("content"));

        employee.disconnect();
        reconnectedPsychologist.disconnect();
        awaitDisconnected("employee-1");
        awaitDisconnected("psychologist-1");
        assertFalse(presenceRegistry.isConnected("employee-1"));
        assertFalse(presenceRegistry.isConnected("psychologist-1"));
    }

    private StompSession connect(String userId, String role, TestSessionHandler handler)
            throws Exception {
        String url = "ws://localhost:" + port + "/ws/chat?token=" + token(userId, role);
        CompletableFuture<StompSession> connection =
                client.connectAsync(url, new WebSocketHttpHeaders(), handler);
        return connection.get(5, TimeUnit.SECONDS);
    }

    private String token(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void awaitConnected(String userId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!presenceRegistry.isConnected(userId) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(presenceRegistry.isConnected(userId), userId + " should be connected");
    }

    private void awaitDisconnected(String userId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (presenceRegistry.isConnected(userId) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(presenceRegistry.isConnected(userId), userId + " should be disconnected");
    }

    private static class TestSessionHandler extends StompSessionHandlerAdapter {
        private final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<Map<String, Object>> errors = new LinkedBlockingQueue<>();

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload instanceof Map<?, ?> frame) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) frame;
                if ("/user/queue/errors".equals(headers.getDestination())) {
                    errors.add(message);
                } else {
                    messages.add(message);
                }
            }
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }
    }
}
