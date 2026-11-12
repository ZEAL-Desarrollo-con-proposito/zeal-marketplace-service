package co.zeal.marketplace.security;

import co.zeal.marketplace.support.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tokens reales firmados (como los del Wellbeing Service): la lista negra de Redis (R2-13) los rechaza
 * por sesión o por jti, y los errores de la API llegan siempre con el mismo formato y sin detalles internos.
 */
@AutoConfigureMockMvc
class ApiErrorsAndRevocationIT extends IntegrationTest {

    private static final String SECRET = "ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!";

    @Autowired
    MockMvc mvc;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void revokedSessionsAndTokensGet401() throws Exception {
        String sessionId = "sesion-" + UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String bySession = token("usr-empleado-001", "EMPLEADO", sessionId, UUID.randomUUID().toString());
        String byJti = token("usr-empleado-001", "EMPLEADO", "otra-" + sessionId, jti);
        mvc.perform(get("/api/proyectos").header("Authorization", "Bearer " + bySession)).andExpect(status().isOk());

        redis.opsForValue().set("zeal:revoked:sid:" + sessionId, "REFRESH_REUTILIZADO", Duration.ofMinutes(1));
        redis.opsForValue().set("zeal:revoked:jti:" + jti, "LOGOUT", Duration.ofMinutes(1));

        mvc.perform(get("/api/proyectos").header("Authorization", "Bearer " + bySession))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status", is(401)));
        mvc.perform(get("/api/proyectos").header("Authorization", "Bearer " + byJti))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBodiesUnsupportedMethodsAndDuplicatesUseTheApiErrorFormat() throws Exception {
        String admin = "Bearer " + token("usr-admin-001", "ADMIN", "s-" + UUID.randomUUID(), UUID.randomUUID().toString());
        String body = "{\"title\":\"Duplicada " + UUID.randomUUID().toString().substring(0, 8) + "\",\"area\":\"Plataforma\","
                + "\"cohort\":\"Sustentación\",\"summary\":\"Diez cupos para quinientas personas.\","
                + "\"description\":[\"Convocatoria creada por la prueba de errores.\"],\"requirements\":[],"
                + "\"skills\":[],\"capacity\":3,\"closesAt\":\"" + Instant.now().plus(Duration.ofDays(2)) + "\"}";

        mvc.perform(post("/api/admin/proyectos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("El cuerpo de la petición no tiene el formato esperado.")));
        mvc.perform(delete("/api/admin/proyectos").header("Authorization", admin))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status", is(405)));
        mvc.perform(post("/api/admin/proyectos").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/proyectos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status", is(409)));
        mvc.perform(get("/api/proyectos/no-existe").header("Authorization",
                        "Bearer " + token("usr-empleado-001", "EMPLEADO", "s-" + UUID.randomUUID(), UUID.randomUUID().toString())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path", is("/api/proyectos/no-existe")));
    }

    private static String token(String userId, String role, String sessionId, String jti) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().id(jti).issuer("zeal").subject(userId).issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15))).claim("role", role).claim("fullName", "Persona")
                .claim("email", userId + "@zeal.co").claim("organization", "Nexura S.A.S.").claim("sid", sessionId).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
