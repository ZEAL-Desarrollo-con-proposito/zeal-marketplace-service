package co.zeal.marketplace.config;

import co.zeal.marketplace.project.Project;
import co.zeal.marketplace.project.ProjectRepository;
import co.zeal.marketplace.workshop.Workshop;
import co.zeal.marketplace.workshop.WorkshopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final ProjectRepository projectRepository;
    private final WorkshopRepository workshopRepository;

    public DataSeeder(ProjectRepository projectRepository, WorkshopRepository workshopRepository) {
        this.projectRepository = projectRepository;
        this.workshopRepository = workshopRepository;
    }

    @Override
    public void run(String... args) {
        if (projectRepository.count() == 0) {
            projectRepository.save(Project.builder()
                    .title("Proyecto Alpha")
                    .description("Automatización de procesos de RRHH con IA")
                    .capacity(10)
                    .enrolled(0)
                    .organizationId("nexura-001")
                    .build());
            log.info("[DataSeeder] Proyecto semilla creado.");
        }
        if (workshopRepository.count() == 0) {
            workshopRepository.save(Workshop.builder()
                    .title("Liderazgo y Comunicación")
                    .description("Taller de desarrollo de habilidades blandas")
                    .capacity(20)
                    .enrolled(0)
                    .organizationId("nexura-001")
                    .build());
            log.info("[DataSeeder] Taller semilla creado.");
        }
    }
}
