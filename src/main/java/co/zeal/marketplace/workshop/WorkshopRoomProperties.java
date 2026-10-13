package co.zeal.marketplace.workshop;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** La sala del taller abre {@code opensBeforeStart} antes del inicio y cierra cuando el taller termina. */
@Validated
@ConfigurationProperties("app.workshop-room")
public record WorkshopRoomProperties(@NotNull Duration opensBeforeStart) {
}
