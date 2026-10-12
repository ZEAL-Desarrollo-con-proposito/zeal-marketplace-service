package co.zeal.marketplace.project;

import java.time.Instant;

/** Contrato de {@code isApplication()} del frontend. */
public record ApplicationView(String id, String projectId, String projectTitle, ApplicationStatus status,
                              Integer waitlistPosition, Instant createdAt, long version) {

    static ApplicationView of(Application application, Project project) {
        return new ApplicationView(application.getId(), project.getId(), project.getTitle(), application.getStatus(),
                application.getWaitlistPosition(), application.getCreatedAt(), application.getVersion());
    }
}
