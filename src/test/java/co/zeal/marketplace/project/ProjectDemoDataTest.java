package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.cluster.ClusterLock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los datos de demostración son coherentes y se siembran una sola vez aunque arranquen varias réplicas. */
class ProjectDemoDataTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ClusterLock lock = mock(ClusterLock.class);
    private final ProjectDemoData demo = new ProjectDemoData(projects, Clock.fixed(NOW, ZoneOffset.UTC), lock);

    @Test
    @SuppressWarnings("unchecked")
    void seedsOpenProjectsForEachCompanyWithoutOverbooking() {
        when(projects.count()).thenReturn(0L);

        demo.run(null);

        ArgumentCaptor<List<Project>> saved = ArgumentCaptor.forClass(List.class);
        verify(projects).saveAll(saved.capture());
        List<Project> seeds = saved.getValue();
        assertThat(seeds).extracting(Project::getId).doesNotHaveDuplicates();
        assertThat(seeds).extracting(Project::getTitle).doesNotHaveDuplicates();
        assertThat(seeds).allSatisfy(project -> {
            assertThat(project.getOccupied()).isBetween(0, project.getCapacity());
            assertThat(project.isOpenAt(NOW)).isTrue();
            assertThat(project.getPublishedAt()).isBeforeOrEqualTo(NOW);
        });
        assertThat(seeds).filteredOn(project -> project.belongsTo("Órbita Labs S.A.S.")).hasSize(1);
        assertThat(seeds).filteredOn(project -> project.getId().equals("proyecto-c1-carga"))
                .singleElement().satisfies(c1 -> {
                    assertThat(c1.getCapacity()).isEqualTo(10);
                    assertThat(c1.getOccupied()).isZero();
                });
    }

    @Test
    void takesTheClusterLockBeforeCheckingAndSkipsWhenDataExists() {
        when(projects.count()).thenReturn(4L);

        demo.run(null);

        InOrder order = inOrder(lock, projects);
        order.verify(lock).acquireForTransaction("demo-data:projects");
        order.verify(projects).count();
        verify(projects, never()).saveAll(any());
    }
}
