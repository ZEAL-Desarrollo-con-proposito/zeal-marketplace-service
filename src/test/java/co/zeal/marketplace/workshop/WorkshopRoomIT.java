package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.video.LiveKitProperties;
import co.zeal.marketplace.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sala de video del taller: quién entra, cuándo abre y qué permiso firma el servidor. */
@AutoConfigureMockMvc
class WorkshopRoomIT extends IntegrationTest {

    private static final String NEXURA = "Nexura S.A.S.";

    @Autowired
    MockMvc mvc;
    @Autowired
    LiveKitProperties liveKit;

    @Test
    void hostAndEnrolledPeopleGetASignedPermissionWhileTheRoomIsOpen() throws Exception {
        String id = publish(Instant.now().plus(5, ChronoUnit.MINUTES));
        mvc.perform(post("/api/talleres/" + id + "/inscripciones").with(user("usr-room-1", "EMPLEADO", NEXURA)))
                .andExpect(status().isCreated());

        String hostBody = mvc.perform(get("/api/talleres/" + id + "/sala").with(user("usr-admin-001", "ADMIN", NEXURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ABIERTA")))
                .andExpect(jsonPath("$.role", is("ANFITRION")))
                .andExpect(jsonPath("$.enrolled", is(1)))
                .andExpect(jsonPath("$.connection.serverUrl", is("/livekit")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/talleres/" + id + "/sala").with(user("usr-room-1", "EMPLEADO", NEXURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("PARTICIPANTE")));

        SignedJWT token = SignedJWT.parse(JsonPath.read(hostBody, "$.connection.token"));
        assertThat(token.verify(new MACVerifier(liveKit.apiSecret().getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(token.getJWTClaimsSet().getIssuer()).isEqualTo(liveKit.apiKey());
        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo("usr-admin-001");
        @SuppressWarnings("unchecked")
        Map<String, Object> video = (Map<String, Object>) token.getJWTClaimsSet().getClaim("video");
        assertThat(video).containsEntry("room", id).containsEntry("roomJoin", true).containsEntry("roomAdmin", true);
    }

    @Test
    void roomIsClosedUntilTenMinutesBeforeTheWorkshop() throws Exception {
        String id = publish(Instant.now().plus(2, ChronoUnit.HOURS));

        mvc.perform(get("/api/talleres/" + id + "/sala").with(user("usr-admin-001", "ADMIN", NEXURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROGRAMADA")))
                .andExpect(jsonPath("$.connection", nullValue()));
    }

    @Test
    void peopleWhoAreNotEnrolledOrFromAnotherCompanyCannotEnter() throws Exception {
        String id = publish(Instant.now().plus(5, ChronoUnit.MINUTES));

        mvc.perform(get("/api/talleres/" + id + "/sala").with(user("usr-curioso", "EMPLEADO", NEXURA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is(WorkshopRoomService.NOT_FOUND)));
        mvc.perform(get("/api/talleres/" + id + "/sala").with(user("usr-admin-otra", "ADMIN", "Órbita Labs S.A.S.")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/talleres/" + id + "/sala").with(user("usr-psicologo-001", "PSICOLOGO", "ZEAL")))
                .andExpect(status().isForbidden());
    }

    private String publish(Instant startsAt) throws Exception {
        String body = """
                {"title":"Taller en vivo %s","description":"Una descripción suficientemente larga.",
                 "startsAt":"%s","durationMinutes":60,"capacity":10}
                """.formatted(java.util.UUID.randomUUID().toString().substring(0, 8), startsAt);
        String response = mvc.perform(post("/api/admin/talleres").with(user("usr-admin-001", "ADMIN", NEXURA))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static JwtRequestPostProcessor user(String id, String role, String organization) {
        return jwt().jwt(token -> token.subject(id).claim("role", role).claim("fullName", "Persona " + id)
                        .claim("email", id + "@empresa.co").claim("organization", organization))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
