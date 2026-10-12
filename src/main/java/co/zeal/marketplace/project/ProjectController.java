package co.zeal.marketplace.project;

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
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/proyectos")
    public List<ProjectView> catalog(@CurrentUser AuthenticatedUser user) {
        return projectService.catalog(user);
    }

    @GetMapping("/api/proyectos/{id}")
    public ProjectDetailView detail(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        return projectService.detail(user, id);
    }

    /** 201 con la postulación nueva (ACEPTADA o EN_ESPERA); 200 si ya existía. */
    @PostMapping("/api/proyectos/{id}/postulaciones")
    public ResponseEntity<ApplicationView> apply(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        ApplicationOutcome outcome = projectService.apply(user, id);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.application());
    }

    @GetMapping("/api/postulaciones/mias")
    public List<ApplicationView> myApplications(@CurrentUser AuthenticatedUser user) {
        return projectService.myApplications(user);
    }
}
