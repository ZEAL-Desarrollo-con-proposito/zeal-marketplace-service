package co.zeal.marketplace.shared.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credenciales del servidor LiveKit (SFU de las videollamadas grupales). {@code publicUrl} es la
 * dirección que recibe el navegador; el secreto solo se usa aquí para firmar los permisos de entrada.
 */
@Validated
@ConfigurationProperties("app.livekit")
public record LiveKitProperties(
        @NotBlank String publicUrl,
        @NotBlank String apiKey,
        @NotBlank @Size(min = 32, message = "El secreto de LiveKit debe tener al menos 32 caracteres") String apiSecret) {
}
