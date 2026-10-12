package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.error.BusinessConflictException;
import co.zeal.marketplace.shared.error.ResourceNotFoundException;
import co.zeal.marketplace.shared.security.AuthenticatedUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Catálogo de convocatorias del empleado y postulación concurrente (HU-16, HU-17, reto C-1). */
@Service
public class ProjectService {

    static final String NOT_FOUND = "Esta convocatoria no existe.";

    private final ProjectRepository projects;
    private final ApplicationRepository applications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ApplicationRepository applications,
                          ApplicationEventPublisher events, Clock clock) {
        this.projects = projects;
        this.applications = applications;
        this.events = events;
        this.clock = clock;
    }

    /** Solo las convocatorias abiertas de la empresa de quien pide. */
    @Transactional(readOnly = true)
    public List<ProjectView> catalog(AuthenticatedUser employee) {
        return projects.findOpen(employee.organization(), Instant.now(clock)).stream().map(ProjectView::of).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailView detail(AuthenticatedUser employee, String projectId) {
        return projects.findByIdAndOrganization(projectId, employee.organization())
                .map(ProjectDetailView::of)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /**
     * Reto C-1 (cero sobreasignación): con la fila bloqueada se revisa el cupo y se asigna en la
     * misma transacción. Quien llega sin cupo entra a la lista de espera con su puesto. Es
     * idempotente: repetir la postulación devuelve la misma.
     */
    @Transactional
    public ApplicationOutcome apply(AuthenticatedUser employee, String projectId) {
        Instant now = Instant.now(clock);
        Project project = projects.findForUpdate(projectId, employee.organization())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        var existing = applications.findByProjectIdAndUserId(project.getId(), employee.id());
        if (existing.isPresent()) {
            return new ApplicationOutcome(ApplicationView.of(existing.get(), project), false);
        }
        if (!project.isOpenAt(now)) {
            throw new BusinessConflictException("Esta convocatoria ya cerró.");
        }
        Application application = project.hasFreeSpot()
                ? Application.accepted(project, employee.id(), now)
                : Application.waitlisted(project, employee.id(), waitlistLength(project) + 1, now);
        applications.save(application);
        applications.flush(); // Materializa las versiones que viajan en la respuesta y en los eventos.
        ApplicationView view = ApplicationView.of(application, project);
        if (application.isAccepted()) {
            events.publishEvent(ProjectEvents.ProjectSpotsChanged.of(project));
        }
        events.publishEvent(new ProjectEvents.ApplicationSubmitted(employee.id(), view));
        return new ApplicationOutcome(view, true);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> myApplications(AuthenticatedUser employee) {
        List<Application> own = applications.findByUserIdOrderByCreatedAtDesc(employee.id());
        Map<String, Project> byId = projects.findAllById(own.stream().map(Application::getProjectId).toList())
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        return own.stream()
                .filter(application -> byId.containsKey(application.getProjectId()))
                .map(application -> ApplicationView.of(application, byId.get(application.getProjectId())))
                .sorted(Comparator.comparing(ApplicationView::createdAt).reversed())
                .toList();
    }

    private int waitlistLength(Project project) {
        return Math.toIntExact(applications.countByProjectIdAndStatus(project.getId(), ApplicationStatus.EN_ESPERA));
    }
}
