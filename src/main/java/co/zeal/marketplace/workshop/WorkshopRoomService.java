package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.error.ResourceNotFoundException;
import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.security.Role;
import co.zeal.marketplace.shared.video.LiveKitTokenIssuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Videollamada grupal de un taller. Entran la empresa que lo publicó (anfitriona) y las personas
 * inscritas; nadie más obtiene permiso de entrada al servidor de video.
 */
@Service
public class WorkshopRoomService {

    static final String NOT_FOUND = "No encontramos la sala de este taller o no estás inscrito.";

    private final WorkshopRepository workshops;
    private final EnrollmentRepository enrollments;
    private final WorkshopRoomProperties properties;
    private final LiveKitTokenIssuer tokens;
    private final Clock clock;

    public WorkshopRoomService(WorkshopRepository workshops, EnrollmentRepository enrollments,
                               WorkshopRoomProperties properties, LiveKitTokenIssuer tokens, Clock clock) {
        this.workshops = workshops;
        this.enrollments = enrollments;
        this.properties = properties;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkshopRoomView roomFor(AuthenticatedUser user, String workshopId) {
        Workshop workshop = workshops.findById(workshopId)
                .filter(found -> found.getOrganization().equals(user.organization()))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        WorkshopRoomView.Role role = roleOf(user, workshop);
        Instant now = Instant.now(clock);
        Instant opensAt = workshop.getStartsAt().minus(properties.opensBeforeStart());
        Instant endsAt = workshop.getStartsAt().plus(Duration.ofMinutes(workshop.getDurationMinutes()));
        WorkshopRoomStatus status = statusAt(now, opensAt, endsAt);
        WorkshopRoomView.Connection connection = status == WorkshopRoomStatus.ABIERTA
                ? new WorkshopRoomView.Connection(tokens.publicUrl(), tokens.issue(grant(user, workshop, role), now, endsAt))
                : null;
        return new WorkshopRoomView(workshop.getId(), workshop.getTitle(), workshop.getStartsAt(), endsAt, opensAt,
                status, role, workshop.getOccupied(), connection);
    }

    private WorkshopRoomView.Role roleOf(AuthenticatedUser user, Workshop workshop) {
        if (user.hasRole(Role.ADMIN)) {
            return WorkshopRoomView.Role.ANFITRION;
        }
        if (user.hasRole(Role.EMPLEADO)
                && enrollments.findByWorkshopIdAndUserId(workshop.getId(), user.id()).isPresent()) {
            return WorkshopRoomView.Role.PARTICIPANTE;
        }
        throw new ResourceNotFoundException(NOT_FOUND);
    }

    private static WorkshopRoomStatus statusAt(Instant now, Instant opensAt, Instant endsAt) {
        if (now.isBefore(opensAt)) {
            return WorkshopRoomStatus.PROGRAMADA;
        }
        return now.isBefore(endsAt) ? WorkshopRoomStatus.ABIERTA : WorkshopRoomStatus.FINALIZADA;
    }

    private static LiveKitTokenIssuer.RoomGrant grant(AuthenticatedUser user, Workshop workshop,
                                                      WorkshopRoomView.Role role) {
        String metadata = "{\"role\":\"" + role.name() + "\"}";
        return new LiveKitTokenIssuer.RoomGrant(workshop.getId(), user.id(), user.fullName(), metadata,
                role == WorkshopRoomView.Role.ANFITRION);
    }
}
