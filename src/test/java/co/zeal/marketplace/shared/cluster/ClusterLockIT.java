package co.zeal.marketplace.shared.cluster;

import co.zeal.marketplace.support.IntegrationTest;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-1: coordinación entre réplicas sobre PostgreSQL. */
class ClusterLockIT extends IntegrationTest {

    @Autowired
    ClusterLock clusterLock;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    LockProvider lockProvider;

    /** Dos "réplicas" siembran a la vez: la segunda entra solo cuando la primera confirmó. */
    @Test
    void secondReplicaWaitsUntilTheFirstCommits() {
        List<String> timeline = new CopyOnWriteArrayList<>();
        Runnable replica = () -> transactions.executeWithoutResult(status -> {
            clusterLock.acquireForTransaction("demo-data:prueba");
            timeline.add("entra");
            sleep(300);
            timeline.add("sale");
        });

        CompletableFuture.allOf(CompletableFuture.runAsync(replica), CompletableFuture.runAsync(replica)).join();

        assertThat(timeline).containsExactly("entra", "sale", "entra", "sale");
    }

    /** ShedLock: mientras una réplica tiene la tarea, otra no la puede tomar. */
    @Test
    void scheduledTaskLockIsExclusiveAcrossReplicas() {
        LockConfiguration task = new LockConfiguration(Instant.now(), "tarea-de-prueba", Duration.ofSeconds(30),
                Duration.ZERO);

        var first = lockProvider.lock(task);
        var second = lockProvider.lock(task);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        first.get().unlock();
        assertThat(lockProvider.lock(task)).isPresent();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
