package co.zeal.marketplace.security;

import co.zeal.marketplace.project.AdminProjectService;
import co.zeal.marketplace.project.AdminProjectView;
import co.zeal.marketplace.project.NewProjectRequest;
import co.zeal.marketplace.support.IntegrationTest;
import co.zeal.marketplace.support.Users;
import co.zeal.marketplace.workshop.AdminWorkshopService;
import co.zeal.marketplace.workshop.NewWorkshopRequest;
import co.zeal.marketplace.workshop.WorkshopView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * R2-10 · Matriz de autorización rol × recurso del Business Domain (SEG-3): empleados y
 * administradores de la empresa dueña y de otra empresa sobre convocatorias, talleres y su sala.
 * Ante lo ajeno: 404 (nunca 403 ni datos). Corre en el CI con {@code ./mvnw verify}.
 */
@AutoConfigureMockMvc
class AuthorizationMatrixIT extends IntegrationTest {

    private static final String ORBITA = "Órbita Labs S.A.S.";

    enum Actor {
        EMPLEADO_NEXURA("usr-empleado-001", "EMPLEADO", Users.NEXURA),
        EMPLEADO_ORBITA("usr-empleado-101", "EMPLEADO", ORBITA),
        ADMIN_NEXURA("usr-admin-001", "ADMIN", Users.NEXURA),
        ADMIN_ORBITA("usr-admin-002", "ADMIN", ORBITA),
        PSICOLOGO("usr-psicologo-001", "PSICOLOGO", "ZEAL");

        final String id;
        final String role;
        final String organization;

        Actor(String id, String role, String organization) {
            this.id = id;
            this.role = role;
            this.organization = organization;
        }

        RequestPostProcessor token() {
            return jwt().jwt(token -> token.subject(id).claim("role", role).claim("fullName", "Persona " + id)
                            .claim("email", id + "@zeal.test").claim("organization", organization))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role));
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    AdminProjectService adminProjects;
    @Autowired
    AdminWorkshopService adminWorkshops;

    private Map<String, String> ids;

    @BeforeEach
    void nexuraPublishesAProjectAndAWorkshop() {
        String run = Long.toString(System.nanoTime(), 36); // Títulos únicos: no se repiten abiertos.
        AdminProjectView project = adminProjects.publish(Users.admin(Users.NEXURA), new NewProjectRequest(
                "Proyecto de la matriz " + run, "Plataforma", "Matriz", "Convocatoria de Nexura para la matriz SEG-3.",
                List.of("Solo para empleados de Nexura."), List.of(), List.of(), 5,
                Instant.now().plus(Duration.ofDays(3))));
        WorkshopView workshop = adminWorkshops.publish(Users.admin(Users.NEXURA), new NewWorkshopRequest(
                "Taller de la matriz " + run, "Taller de Nexura para la matriz SEG-3.",
                Instant.now().plus(Duration.ofMinutes(5)), 60, 10));
        ids = Map.of("{proyecto}", project.id(), "{taller}", workshop.id());
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                row("GET", "/api/proyectos/{proyecto}", Actor.EMPLEADO_NEXURA, 200),
                row("GET", "/api/proyectos/{proyecto}", Actor.EMPLEADO_ORBITA, 404),
                row("GET", "/api/proyectos/{proyecto}", Actor.ADMIN_NEXURA, 404),
                row("GET", "/api/proyectos/{proyecto}", Actor.PSICOLOGO, 404),
                row("POST", "/api/proyectos/{proyecto}/postulaciones", Actor.EMPLEADO_NEXURA, 201),
                row("POST", "/api/proyectos/{proyecto}/postulaciones", Actor.EMPLEADO_ORBITA, 404),
                row("POST", "/api/proyectos/{proyecto}/postulaciones", Actor.ADMIN_NEXURA, 404),
                row("POST", "/api/talleres/{taller}/inscripciones", Actor.EMPLEADO_NEXURA, 201),
                row("POST", "/api/talleres/{taller}/inscripciones", Actor.EMPLEADO_ORBITA, 404),
                row("POST", "/api/talleres/{taller}/inscripciones", Actor.ADMIN_ORBITA, 404),
                // Sala del taller: la empresa anfitriona y sus inscritos (aquí nadie está inscrito aún).
                row("GET", "/api/talleres/{taller}/sala", Actor.ADMIN_NEXURA, 200),
                row("GET", "/api/talleres/{taller}/sala", Actor.ADMIN_ORBITA, 404),
                row("GET", "/api/talleres/{taller}/sala", Actor.EMPLEADO_NEXURA, 404),
                row("GET", "/api/talleres/{taller}/sala", Actor.EMPLEADO_ORBITA, 404),
                row("GET", "/api/talleres/{taller}/sala", Actor.PSICOLOGO, 404),
                // Paneles de administración: solo administradores.
                row("GET", "/api/admin/proyectos", Actor.ADMIN_NEXURA, 200),
                row("GET", "/api/admin/proyectos", Actor.EMPLEADO_NEXURA, 404),
                row("GET", "/api/admin/talleres", Actor.ADMIN_ORBITA, 200),
                row("GET", "/api/admin/talleres", Actor.PSICOLOGO, 404),
                // Listados propios: solo empleados.
                row("GET", "/api/postulaciones/mias", Actor.EMPLEADO_ORBITA, 200),
                row("GET", "/api/postulaciones/mias", Actor.ADMIN_NEXURA, 404),
                row("GET", "/api/inscripciones/mias", Actor.EMPLEADO_NEXURA, 200),
                row("GET", "/api/inscripciones/mias", Actor.PSICOLOGO, 404));
    }

    @ParameterizedTest(name = "{0} {1} como {2} → {3}")
    @MethodSource("matrix")
    void eachRoleAndCompanyOnlyReachesItsOwnResources(String method, String path, Actor actor, int expected)
            throws Exception {
        String uri = path.replace("{proyecto}", ids.get("{proyecto}")).replace("{taller}", ids.get("{taller}"));

        int status = mvc.perform(request(HttpMethod.valueOf(method), uri).with(actor.token()))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("%s %s como %s", method, uri, actor).isEqualTo(expected);
    }

    /** 0 datos ajenos expuestos: los catálogos de otra empresa no incluyen la convocatoria ni el taller. */
    @Test
    void catalogsNeverListAnotherCompanysOffers() throws Exception {
        String projects = mvc.perform(get("/api/proyectos").with(Actor.EMPLEADO_ORBITA.token()))
                .andReturn().getResponse().getContentAsString();
        String workshops = mvc.perform(get("/api/talleres").with(Actor.EMPLEADO_ORBITA.token()))
                .andReturn().getResponse().getContentAsString();
        String adminPanel = mvc.perform(get("/api/admin/proyectos").with(Actor.ADMIN_ORBITA.token()))
                .andReturn().getResponse().getContentAsString();

        assertThat(projects).doesNotContain(ids.get("{proyecto}"));
        assertThat(workshops).doesNotContain(ids.get("{taller}"));
        assertThat(adminPanel).doesNotContain(ids.get("{proyecto}"));
    }

    private static Arguments row(String method, String path, Actor actor, int expected) {
        return Arguments.of(method, path, actor, expected);
    }
}
