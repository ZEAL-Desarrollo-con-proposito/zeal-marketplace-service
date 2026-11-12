package co.zeal.marketplace.shared.cache;

import co.zeal.marketplace.support.IntegrationTest;
import co.zeal.marketplace.support.Users;
import co.zeal.marketplace.workshop.AdminWorkshopService;
import co.zeal.marketplace.workshop.NewWorkshopRequest;
import co.zeal.marketplace.workshop.WorkshopService;
import co.zeal.marketplace.workshop.WorkshopView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-26 · Catálogo de talleres en Redis, una entrada por empresa, invalidado por los eventos de talleres. */
class WorkshopCatalogCacheIT extends IntegrationTest {

    @Autowired
    WorkshopService workshops;
    @Autowired
    AdminWorkshopService admin;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    CacheManager caches;

    @Test
    void eachCompanyHasItsOwnCachedCatalogRefreshedWhenAWorkshopIsPublished() throws Exception {
        caches.getCache(CacheConfig.CACHE).clear();
        workshops.upcoming(Users.employee("e-1", Users.NEXURA));
        workshops.upcoming(Users.employee("e-2", "Órbita Labs S.A.S."));
        assertThat(redis.keys("zeal:cache:workshop-catalog::*")).hasSize(2);

        WorkshopView published = admin.publish(Users.admin(Users.NEXURA), new NewWorkshopRequest(
                "Taller de caché " + System.nanoTime(), "Taller creado por la prueba de caché.",
                Instant.now().plus(Duration.ofDays(1)), 60, 10));
        redis.convertAndSend("zeal.marketplace.workshops.published", "{\"organization\":\"Nexura S.A.S.\"}");
        long deadline = System.currentTimeMillis() + 3000;
        while (!redis.keys("zeal:cache:workshop-catalog::*").isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertThat(workshops.upcoming(Users.employee("e-1", Users.NEXURA))).extracting(WorkshopView::id)
                .contains(published.id());
        assertThat(workshops.upcoming(Users.employee("e-2", "Órbita Labs S.A.S."))).extracting(WorkshopView::id)
                .doesNotContain(published.id());
    }
}
