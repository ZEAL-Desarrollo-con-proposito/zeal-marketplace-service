package co.zeal.marketplace.project;

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
@RequestMapping("/api/admin/proyectos")
public class AdminProjectController {

    private final AdminProjectService adminProjectService;

    public AdminProjectController(AdminProjectService adminProjectService) {
        this.adminProjectService = adminProjectService;
    }

    @GetMapping
    public List<AdminProjectView> list(@CurrentUser AuthenticatedUser admin) {
        return adminProjectService.list(admin);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProjectView publish(@CurrentUser AuthenticatedUser admin, @RequestBody NewProjectRequest request) {
        return adminProjectService.publish(admin, request);
    }
}
