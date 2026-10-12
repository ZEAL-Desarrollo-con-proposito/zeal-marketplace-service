package co.zeal.marketplace.workshop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** Talleres de demostración (los del simulador del frontend), relativos al arranque. */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
class WorkshopDemoData implements ApplicationRunner {

    private static final String NEXURA = "Nexura S.A.S.";
    private static final String OTHER = "Órbita Labs S.A.S.";
    private static final Logger log = LoggerFactory.getLogger(WorkshopDemoData.class);

    private final WorkshopRepository workshops;
    private final Clock clock;
    private final ZoneId zone;

    WorkshopDemoData(WorkshopRepository workshops, Clock clock, ZoneId businessZone) {
        this.workshops = workshops;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (workshops.count() > 0) {
            return;
        }
        Instant now = Instant.now(clock);
        List<Workshop> seeds = List.of(
                seed("taller-pausas-activas", NEXURA, now, 2, 12, "Pausas activas y respiración",
                        "Aprende técnicas breves de respiración y pausas activas para tu jornada.", dayAt(7, 15), 60, 30),
                seed("taller-malestar-agil", NEXURA, now, 3, 12, "Gestión del malestar en equipos ágiles",
                        "Cómo nombrar la sobrecarga sin quedar como “el que no aguanta”. Facilita Gabriela Muñoz, psicóloga organizacional.",
                        now.plus(Duration.ofMinutes(45)), 50, 30),
                seed("taller-limites-sanos", NEXURA, now, 4, 30, "Límites sanos y desconexión digital",
                        "Acuerdos de equipo para dejar de responder mensajes a medianoche.",
                        now.plus(Duration.ofHours(3)), 60, 30),
                seed("taller-impostor", NEXURA, now, 5, 29, "Síndrome del impostor en tu primer empleo",
                        "Herramientas para separar lo que sientes de lo que de verdad aportas.", dayAt(1, 12), 60, 30),
                seed("taller-descanso-real", NEXURA, now, 6, 8, "Descanso real: dormir en semana de release",
                        "Higiene del sueño para las semanas de entrega más exigentes.", dayAt(5, 7), 45, 25),
                seed("taller-conversaciones-dificiles", NEXURA, now, 7, 17, "Conversaciones difíciles con tu líder",
                        "Cómo pedir ayuda, poner límites o dar una mala noticia sin romper la confianza.",
                        dayAt(6, 16), 60, 30),
                seed("taller-primeros-auxilios", NEXURA, now, 9, 5, "Primeros auxilios psicológicos para equipos",
                        "Qué hacer, y qué no, cuando alguien de tu equipo está pasando un mal momento.",
                        dayAt(8, 15), 90, 20),
                seed("taller-otra-empresa", OTHER, now, 1, 0, "Taller de otra empresa",
                        "Pertenece a otra organización y nunca debe verse aquí.", dayAt(2, 10), 60, 10));
        workshops.saveAll(seeds);
        log.info("[demo] Sembrados {} talleres de demostración.", seeds.size());
    }

    private Instant dayAt(int daysFromToday, int hour) {
        return LocalDate.now(clock.withZone(zone)).plusDays(daysFromToday).atTime(LocalTime.of(hour, 0))
                .atZone(zone).toInstant();
    }

    private static Workshop seed(String id, String organization, Instant now, int publishedDaysAgo, int occupied,
                                 String title, String description, Instant startsAt, int durationMinutes,
                                 int capacity) {
        WorkshopDraft draft = new WorkshopDraft(title, description, startsAt, durationMinutes, capacity);
        return Workshop.seed(id, organization, draft, occupied, now.minus(Duration.ofDays(publishedDaysAgo)));
    }
}
