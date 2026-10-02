package co.zeal.marketplace.project;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/marketplace/proyectos")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public ProjectController(ProjectRepository projectRepository, RedisTemplate<String, Object> redisTemplate) {
        this.projectRepository = projectRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public List<Project> list() { return projectRepository.findAll(); }

    @GetMapping("/{id}")
    public Project getOne(@PathVariable Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@RequestBody ProjectRequest body, HttpServletRequest req) {
        String role = (String) req.getAttribute("userRole");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administradores pueden crear proyectos.");
        }
        Project project = Project.builder()
                .title(body.title())
                .description(body.description())
                .capacity(body.capacity())
                .enrolled(0)
                .organizationId((String) req.getAttribute("userId"))
                .build();
        projectRepository.save(project);
        redisTemplate.convertAndSend("events.projects", Map.of(
                "type", "PROYECTO_PUBLICADO",
                "project", Map.of("id", project.getId(), "title", project.getTitle(),
                        "capacity", project.getCapacity(), "enrolled", project.getEnrolled())
        ));
        return project;
    }

    /** HU-17 / RETO C-1: 0% overbooking con SELECT FOR UPDATE */
    @Transactional
    @PostMapping("/{id}/postulaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResult apply(@PathVariable Long id, HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        Project project = projectRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (project.getEnrolled() >= project.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cupos agotados.");
        }
        project.setEnrolled(project.getEnrolled() + 1);
        projectRepository.save(project);
        int remaining = project.getCapacity() - project.getEnrolled();
        redisTemplate.convertAndSend("events.projects.capacity",
                Map.of("projectId", id, "remainingSlots", remaining));
        return new ApplicationResult(id, userId, "ACEPTADO", remaining);
    }

    public record ProjectRequest(String title, String description, Integer capacity) {}
    public record ApplicationResult(Long projectId, String userId, String status, int remainingSlots) {}
}
