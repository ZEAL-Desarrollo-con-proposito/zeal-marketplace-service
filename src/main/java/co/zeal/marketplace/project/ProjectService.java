package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.error.BusinessConflictException;
import co.zeal.marketplace.shared.error.ResourceNotFoundException;
import co.zeal.marketplace.shared.security.AuthenticatedUser;
import org.springframework.context.ApplicationEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
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
    private final SpotClaims claims;
    private final TransactionTemplate transactions;
    private final Timer saveTimer;

    public ProjectService(ProjectRepository projects, ApplicationRepository applications,
                          ApplicationEventPublisher events, Clock clock, SpotClaims claims,
                          PlatformTransactionManager transactionManager, MeterRegistry meters) {
        this.claims = claims;
        // REND-2: cuánto tarda guardar la postulación ya con el cupo reclamado (conexión + insert + commit + eventos).
        this.saveTimer = Timer.builder("zeal.marketplace.application.save").publishPercentileHistogram().register(meters);
        this.transactions = new TransactionTemplate(transactionManager);
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
     * Reto C-1 (cero sobreasignación) y REND-2: el cupo o el puesto en la lista de espera se reclama con
     * una sentencia atómica ({@link SpotClaims}); después se guarda la postulación. Es idempotente:
     * repetir la postulación devuelve la misma.
     */
    /**
     * Dobles envíos de la MISMA persona a la MISMA convocatoria en esta réplica se atienden de a uno: el segundo ve la
     * postulación ya guardada y no reclama otro cupo. Entre réplicas, la restricción única de la base y la
     * compensación de {@link #applyOnce} siguen siendo la red de seguridad.
     */
    private static final int PERSON_LOCKS = 256;
    private final ReentrantLock[] personLocks = IntStream.range(0, PERSON_LOCKS)
            .mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public ApplicationOutcome apply(AuthenticatedUser employee, String projectId) {
        ReentrantLock lock = personLocks[Math.floorMod((projectId + '|' + employee.id()).hashCode(), PERSON_LOCKS)];
        lock.lock();
        try {
            return applyOnce(employee, projectId);
        } finally {
            lock.unlock();
        }
    }

    private ApplicationOutcome applyOnce(AuthenticatedUser employee, String projectId) {
        Instant now = Instant.now(clock);
        var existing = applications.findByProjectIdAndUserId(projectId, employee.id());
        if (existing.isPresent()) {
            return new ApplicationOutcome(ApplicationView.of(existing.get(), project(employee, projectId)), false);
        }
        SpotClaims.Claim claim = claims.claim(projectId, employee.organization(), now).orElseThrow(() -> {
            project(employee, projectId); // 404 si no existe para esta empresa
            return new BusinessConflictException("Esta convocatoria ya cerró.");
        });
        try {
            return saveTimer.record(() -> transactions.execute(status -> {
                Project project = project(employee, projectId);
                Application application = applications.saveAndFlush(
                        Application.claimed(projectId, employee.id(), claim, now));
                ApplicationView view = ApplicationView.of(application, project);
                if (claim.accepted()) {
                    events.publishEvent(new ProjectEvents.ProjectSpotsChanged(projectId, claim.capacity(),
                            claim.occupied(), claim.version()));
                }
                events.publishEvent(new ProjectEvents.ApplicationSubmitted(employee.id(), view));
                return new ApplicationOutcome(view, true);
            }));
        } catch (DataIntegrityViolationException duplicate) {
            // Doble envío simultáneo desde otra réplica: la otra petición de la misma persona ya guardó su postulación.
            Application stored = applications.findByProjectIdAndUserId(projectId, employee.id()).orElseThrow(() -> duplicate);
            if (claim.accepted()) {
                if (stored.getStatus() == ApplicationStatus.ACEPTADA) {
                    claims.release(projectId);
                } else {
                    claims.promoteStoredToAccepted(stored.getId(), projectId);
                    stored = applications.findByProjectIdAndUserId(projectId, employee.id()).orElseThrow(() -> duplicate);
                }
            } else {
                claims.releaseWaitlist(projectId);
            }
            return new ApplicationOutcome(ApplicationView.of(stored, project(employee, projectId)), false);
        }
    }

    private Project project(AuthenticatedUser employee, String projectId) {
        return projects.findByIdAndOrganization(projectId, employee.organization())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
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
}
