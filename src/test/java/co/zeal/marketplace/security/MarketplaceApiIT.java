package co.zeal.marketplace.security;

import co.zeal.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autorización por rol y aislamiento entre empresas sobre la API HTTP real. */
@AutoConfigureMockMvc
class MarketplaceApiIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void employeesCannotPublish() throws Exception {
        mvc.perform(get("/api/admin/proyectos").with(user("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.")))
                .andExpect(status().isNotFound()) // SEG-3: 404 en lugar de 403
                .andExpect(jsonPath("$.message", is("El recurso solicitado no existe.")));
    }

    @Test
    void invalidDraftIsRejectedWithTheRuleThatFailed() throws Exception {
        mvc.perform(post("/api/admin/proyectos").with(user("usr-admin-001", "ADMIN", "Nexura S.A.S."))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hey\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("El título debe tener entre 5 y 80 caracteres")));
    }

    @Test
    void publishedProjectIsOnlyVisibleInsideTheOrganization() throws Exception {
        String body = """
                {"title":"Convocatoria privada de Nexura","area":"Datos","cohort":"2026-2",
                 "summary":"Solo la ve la gente de Nexura.","description":["Una descripción suficientemente larga."],
                 "requirements":[],"skills":[],"capacity":3,"closesAt":"%s"}
                """.formatted(Instant.now().plus(5, ChronoUnit.DAYS));
        String response = mvc.perform(post("/api/admin/proyectos").with(user("usr-admin-001", "ADMIN", "Nexura S.A.S."))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occupied", is(0)))
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/proyectos/" + id).with(user("usr-otro", "EMPLEADO", "Órbita Labs S.A.S.")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/proyectos/" + id + "/postulaciones")
                        .with(user("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACEPTADA")))
                .andExpect(jsonPath("$.projectId", is(id)));
        mvc.perform(post("/api/proyectos/" + id + "/postulaciones")
                        .with(user("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.")))
                .andExpect(status().isOk());
    }

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mvc.perform(get("/api/proyectos")).andExpect(status().isUnauthorized());
    }

    private static JwtRequestPostProcessor user(String id, String role, String organization) {
        return jwt().jwt(token -> token.subject(id).claim("role", role).claim("fullName", "Persona " + id)
                        .claim("email", id + "@empresa.co").claim("organization", organization))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }
}
