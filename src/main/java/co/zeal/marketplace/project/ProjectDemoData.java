package co.zeal.marketplace.project;

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
import java.util.List;

/**
 * Convocatorias de demostración (las mismas del simulador del frontend), con fechas relativas al
 * arranque. {@code proyecto-c1-carga} es el objetivo de la prueba de carga C-1 (10 cupos).
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
class ProjectDemoData implements ApplicationRunner {

    static final String NEXURA = "Nexura S.A.S.";
    static final String OTHER = "Órbita Labs S.A.S.";
    private static final Logger log = LoggerFactory.getLogger(ProjectDemoData.class);

    private final ProjectRepository projects;
    private final Clock clock;

    ProjectDemoData(ProjectRepository projects, Clock clock) {
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (projects.count() > 0) {
            return;
        }
        Instant now = Instant.now(clock);
        List<Project> seeds = List.of(
                seed("proyecto-observabilidad", NEXURA, now, 3, 3,
                        draft("Squad de Observabilidad", "Plataforma", "Semilla 2026-2",
                                "Instrumentar trazas y métricas del core de reservas.",
                                List.of("Vas a instrumentar trazas y métricas del core de reservas junto al equipo de plataforma: definir los SLI del flujo de agendamiento, montar los dashboards y dejar alertas que avisen antes de que el usuario note algo.",
                                        "Dedicación de 6 horas semanales durante un trimestre, sin dejar tu equipo actual. Tu líder ya avaló la convocatoria."),
                                List.of("Haber hecho al menos un feature end-to-end", "Manejo básico de logs y métricas",
                                        "Ganas de documentar lo que aprendas"),
                                List.of("Liderar una iniciativa · hito 4", "Gestión de incidentes +12%",
                                        "Diseño de sistemas +8%"), 3, now, 2)),
                seed("proyecto-mentoria-inversa", NEXURA, now, 6, 5,
                        draft("Mentoría inversa Gen Z", "Cultura", "Talento Humano",
                                "Enséñale a un líder senior cómo trabaja tu generación.",
                                List.of("Te emparejamos con una persona del equipo directivo durante dos meses para compartir herramientas, hábitos y formas de colaborar.",
                                        "Una conversación quincenal de 45 minutos, con una guía de temas que puedes ajustar."),
                                List.of("Curiosidad y paciencia para explicar", "Disponibilidad quincenal"),
                                List.of("Comunicación +10%", "Influencia sin autoridad · hito 2"), 8, now, 9)),
                seed("proyecto-guardia-datos", NEXURA, now, 9, 2,
                        draft("Data Platform · Turno de guardia", "Datos", "Rotación trimestral",
                                "Acompañar el oncall con un mentor durante 6 semanas.",
                                List.of("Rotarás en el turno de guardia de la plataforma de datos acompañada por un mentor: atenderás alertas, escribirás postmortems y mejorarás los runbooks."),
                                List.of("Conocer SQL", "Disponibilidad para una guardia por semana"),
                                List.of("Gestión de incidentes +15%", "Datos en producción +6%"), 4, now, 5)),
                seed("proyecto-accesibilidad", NEXURA, now, 12, 1,
                        draft("Accesibilidad en producto", "Producto", "Iniciativa transversal",
                                "Auditar y corregir el flujo de reserva con criterios WCAG.",
                                List.of("Auditarás el flujo de reserva con lectores de pantalla y navegación por teclado, y propondrás correcciones priorizadas según WCAG 2.1 AA."),
                                List.of("Interés en accesibilidad", "Ganas de probar con usuarios reales"),
                                List.of("Diseño inclusivo +12%", "Calidad de producto +5%"), 6, now, 12)),
                seed("proyecto-c1-carga", NEXURA, now, 0, 0,
                        draft("Proyecto Alpha · prueba de carga C-1", "Plataforma", "Sustentación",
                                "Convocatoria de 10 cupos para la prueba de 500 postulaciones simultáneas.",
                                List.of("Esta convocatoria existe para demostrar que, con 500 postulaciones simultáneas, se asignan exactamente 10 cupos."),
                                List.of(), List.of(), 10, now, 30)),
                seed("proyecto-otra-empresa", OTHER, now, 1, 0,
                        draft("Rediseño del onboarding comercial", "Ventas", "Semilla 2026-2",
                                "Convocatoria de Órbita Labs para mejorar la llegada de clientes nuevos.",
                                List.of("Convocatoria interna de Órbita Labs: Nexura nunca la ve."), List.of(),
                                List.of(), 2, now, 7)));
        projects.saveAll(seeds);
        log.info("[demo] Sembradas {} convocatorias de demostración.", seeds.size());
    }

    private static ProjectDraft draft(String title, String area, String cohort, String summary,
                                      List<String> description, List<String> requirements, List<String> skills,
                                      int capacity, Instant now, int closesInDays) {
        return new ProjectDraft(title, area, cohort, summary, description, requirements, skills, capacity,
                now.plus(Duration.ofDays(closesInDays)));
    }

    private static Project seed(String id, String organization, Instant now, int publishedDaysAgo, int occupied,
                                ProjectDraft draft) {
        return Project.seed(id, organization, draft, occupied, now.minus(Duration.ofDays(publishedDaysAgo)));
    }
}
