package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.error.BusinessConflictException;
import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.text.TextRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** HU-14: el administrador publica y consulta las convocatorias de su empresa. */
@Service
public class AdminProjectService {

    private final ProjectRepository projects;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AdminProjectService(ProjectRepository projects, ApplicationEventPublisher events, Clock clock) {
        this.projects = projects;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminProjectView> list(AuthenticatedUser admin) {
        return projects.findByOrganizationOrderByPublishedAtDesc(admin.organization()).stream()
                .map(AdminProjectView::of)
                .toList();
    }

    @Transactional
    public AdminProjectView publish(AuthenticatedUser admin, NewProjectRequest request) {
        Instant now = Instant.now(clock);
        ProjectDraft draft = ProjectDraftRules.validate(request, now);
        String key = TextRules.comparisonKey(draft.title());
        boolean repeated = projects.findOpen(admin.organization(), now).stream()
                .anyMatch(open -> TextRules.comparisonKey(open.getTitle()).equals(key));
        if (repeated) {
            throw new BusinessConflictException("Ya tienes un proyecto abierto con ese título");
        }
        Project project = projects.saveAndFlush(Project.publish(admin.organization(), draft, now));
        AdminProjectView view = AdminProjectView.of(project);
        events.publishEvent(new ProjectEvents.ProjectPublished(admin.organization(), view,
                ProjectDetailView.of(project)));
        return view;
    }
}
