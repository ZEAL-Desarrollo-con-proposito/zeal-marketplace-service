package co.zeal.marketplace.workshop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/marketplace/talleres")
public class WorkshopController {

    private final WorkshopRepository workshopRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public WorkshopController(WorkshopRepository workshopRepository, RedisTemplate<String, Object> redisTemplate) {
        this.workshopRepository = workshopRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public List<Workshop> list() { return workshopRepository.findAll(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Workshop create(@RequestBody WorkshopRequest body, HttpServletRequest req) {
        String role = (String) req.getAttribute("userRole");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Workshop workshop = Workshop.builder()
                .title(body.title())
                .description(body.description())
                .capacity(body.capacity())
                .enrolled(0)
                .build();
        workshopRepository.save(workshop);
        redisTemplate.convertAndSend("events.projects", Map.of(
                "type", "TALLER_PUBLICADO",
                "workshop", Map.of("id", workshop.getId(), "title", workshop.getTitle(),
                        "capacity", workshop.getCapacity())
        ));
        return workshop;
    }

    /** HU-18 / RETO C-1: 0% overbooking en talleres */
    @Transactional
    @PostMapping("/{id}/inscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResult enroll(@PathVariable Long id, HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        Workshop workshop = workshopRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (workshop.getEnrolled() >= workshop.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Capacidad máxima alcanzada.");
        }
        workshop.setEnrolled(workshop.getEnrolled() + 1);
        workshopRepository.save(workshop);
        return new EnrollmentResult(id, userId, "INSCRITO", workshop.getCapacity() - workshop.getEnrolled());
    }

    public record WorkshopRequest(String title, String description, Integer capacity) {}
    public record EnrollmentResult(Long workshopId, String userId, String status, int remainingSlots) {}
}
