package co.zeal.marketplace.workshop;

import java.time.Instant;

/**
 * Sala de un taller vista por una persona con acceso. {@code connection} solo viene mientras la sala
 * está abierta: es la URL del servidor de video y el permiso de entrada firmado para esa persona.
 */
public record WorkshopRoomView(String workshopId, String title, Instant startsAt, Instant endsAt, Instant opensAt,
                               WorkshopRoomStatus status, Role role, int enrolled, Connection connection) {

    /** ANFITRION: la empresa que dicta el taller. PARTICIPANTE: una persona inscrita. */
    public enum Role {
        ANFITRION,
        PARTICIPANTE
    }

    public record Connection(String serverUrl, String token) {
    }
}
