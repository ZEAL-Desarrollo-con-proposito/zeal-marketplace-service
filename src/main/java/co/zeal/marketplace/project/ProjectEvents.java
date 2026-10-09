package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.events.Channels;
import co.zeal.marketplace.shared.events.IntegrationEvent;

/** Eventos de integración de las convocatorias (RT-2). */
public final class ProjectEvents {

    private ProjectEvents() {
    }

    /** El gateway avisa al catálogo de la organización y a sus administradores conectados. */
    public record ProjectPublished(String organization, AdminProjectView project, ProjectDetailView detail)
            implements IntegrationEvent {

        @Override
        public String channel() {
            return Channels.PROJECT_PUBLISHED;
        }
    }

    /** HU-16: contador de cupos para quienes miran la convocatoria. */
    public record ProjectSpotsChanged(String projectId, int capacity, int occupied, long version)
            implements IntegrationEvent {

        static ProjectSpotsChanged of(Project project) {
            return new ProjectSpotsChanged(project.getId(), project.getCapacity(), project.getOccupied(),
                    project.getVersion());
        }

        @Override
        public String channel() {
            return Channels.PROJECT_SPOTS;
        }
    }

    /** Resultado de la postulación en la cola privada de quien se postuló (HU-41). */
    public record ApplicationSubmitted(String userId, ApplicationView application) implements IntegrationEvent {

        @Override
        public String channel() {
            return Channels.APPLICATIONS;
        }
    }
}
