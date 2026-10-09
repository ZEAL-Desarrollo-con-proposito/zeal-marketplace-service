package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.error.BusinessConflictException;
import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.text.TextRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** HU-15: el administrador publica y consulta los talleres de su empresa. */
@Service
public class AdminWorkshopService {

    private final WorkshopRepository workshops;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AdminWorkshopService(WorkshopRepository workshops, ApplicationEventPublisher events, Clock clock) {
        this.workshops = workshops;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkshopView> list(AuthenticatedUser admin) {
        return workshops.findByOrganizationOrderByPublishedAtDesc(admin.organization()).stream()
                .map(WorkshopView::of)
                .toList();
    }

    @Transactional
    public WorkshopView publish(AuthenticatedUser admin, NewWorkshopRequest request) {
        Instant now = Instant.now(clock);
        WorkshopDraft draft = WorkshopDraftRules.validate(request, now);
        String key = TextRules.comparisonKey(draft.title());
        boolean repeated = workshops.findUpcoming(admin.organization(), now).stream()
                .anyMatch(upcoming -> TextRules.comparisonKey(upcoming.getTitle()).equals(key));
        if (repeated) {
            throw new BusinessConflictException("Ya tienes un taller próximo con ese título");
        }
        Workshop workshop = workshops.saveAndFlush(Workshop.publish(admin.organization(), draft, now));
        WorkshopView view = WorkshopView.of(workshop);
        events.publishEvent(new WorkshopEvents.WorkshopPublished(admin.organization(), view));
        return view;
    }
}
