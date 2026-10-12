package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('EMPLEADO')")
public class WorkshopController {

    private final WorkshopService workshopService;

    public WorkshopController(WorkshopService workshopService) {
        this.workshopService = workshopService;
    }

    @GetMapping("/api/talleres")
    public List<WorkshopView> upcoming(@CurrentUser AuthenticatedUser user) {
        return workshopService.upcoming(user);
    }

    /** 201 con la inscripción nueva; 200 si ya existía. */
    @PostMapping("/api/talleres/{id}/inscripciones")
    public ResponseEntity<EnrollmentView> enroll(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        EnrollmentOutcome outcome = workshopService.enroll(user, id);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.enrollment());
    }

    @GetMapping("/api/inscripciones/mias")
    public List<EnrollmentView> myEnrollments(@CurrentUser AuthenticatedUser user) {
        return workshopService.myEnrollments(user);
    }
}
