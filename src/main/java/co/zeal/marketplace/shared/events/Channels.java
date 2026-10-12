package co.zeal.marketplace.shared.events;

/** Canales Redis Pub/Sub que publica este servicio. El Real-Time Gateway escucha {@code zeal.*}. */
public final class Channels {

    public static final String PROJECT_PUBLISHED = "zeal.marketplace.projects.published";
    public static final String PROJECT_SPOTS = "zeal.marketplace.projects.spots";
    public static final String APPLICATIONS = "zeal.marketplace.applications";
    public static final String WORKSHOP_PUBLISHED = "zeal.marketplace.workshops.published";
    public static final String WORKSHOP_SPOTS = "zeal.marketplace.workshops.spots";
    public static final String ENROLLMENTS = "zeal.marketplace.enrollments";

    private Channels() {
    }
}
