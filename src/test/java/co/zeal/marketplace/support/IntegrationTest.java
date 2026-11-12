package co.zeal.marketplace.support;

import co.zeal.marketplace.shared.events.IntegrationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integración: PostgreSQL real (los bloqueos FOR UPDATE no se pueden
 * simular en memoria) y el broker reemplazado por un doble para inspeccionar los eventos.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    /** Lista negra de tokens (R2-13). */
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @MockitoBean
    protected IntegrationEventPublisher eventPublisher;
}
