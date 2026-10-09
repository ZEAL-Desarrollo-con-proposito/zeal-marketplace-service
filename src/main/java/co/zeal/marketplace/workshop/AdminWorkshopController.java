package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code /api/admin/**} es exclusivo del rol ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/admin/talleres")
public class AdminWorkshopController {

    private final AdminWorkshopService adminWorkshopService;

    public AdminWorkshopController(AdminWorkshopService adminWorkshopService) {
        this.adminWorkshopService = adminWorkshopService;
    }

    @GetMapping
    public List<WorkshopView> list(@CurrentUser AuthenticatedUser admin) {
        return adminWorkshopService.list(admin);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkshopView publish(@CurrentUser AuthenticatedUser admin, @RequestBody NewWorkshopRequest request) {
        return adminWorkshopService.publish(admin, request);
    }
}
