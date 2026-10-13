package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Sala de video del taller: la empresa anfitriona y las personas inscritas. */
@RestController
@PreAuthorize("hasAnyRole('EMPLEADO', 'ADMIN')")
public class WorkshopRoomController {

    private final WorkshopRoomService rooms;

    public WorkshopRoomController(WorkshopRoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping("/api/talleres/{id}/sala")
    public WorkshopRoomView room(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        return rooms.roomFor(user, id);
    }
}
