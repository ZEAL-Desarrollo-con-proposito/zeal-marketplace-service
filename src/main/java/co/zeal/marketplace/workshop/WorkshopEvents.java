package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.events.Channels;
import co.zeal.marketplace.shared.events.IntegrationEvent;

/** Eventos de integración de los talleres (RT-2). */
public final class WorkshopEvents {

    private WorkshopEvents() {
    }

    public record WorkshopPublished(String organization, WorkshopView workshop) implements IntegrationEvent {

        @Override
        public String channel() {
            return Channels.WORKSHOP_PUBLISHED;
        }
    }

    public record WorkshopSpotsChanged(String workshopId, int capacity, int occupied, long version)
            implements IntegrationEvent {

        static WorkshopSpotsChanged of(Workshop workshop) {
            return new WorkshopSpotsChanged(workshop.getId(), workshop.getCapacity(), workshop.getOccupied(),
                    workshop.getVersion());
        }

        @Override
        public String channel() {
            return Channels.WORKSHOP_SPOTS;
        }
    }

    public record EnrollmentCreated(String userId, EnrollmentView enrollment) implements IntegrationEvent {

        @Override
        public String channel() {
            return Channels.ENROLLMENTS;
        }
    }
}
