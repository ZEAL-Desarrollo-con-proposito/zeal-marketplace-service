package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.cluster.ClusterLock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkshopDemoDataTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    private final WorkshopRepository workshops = mock(WorkshopRepository.class);
    private final ClusterLock lock = mock(ClusterLock.class);
    private final WorkshopDemoData demo = new WorkshopDemoData(workshops, Clock.fixed(NOW, ZoneOffset.UTC), lock,
            ZoneId.of("America/Bogota"));

    @Test
    @SuppressWarnings("unchecked")
    void seedsUpcomingWorkshopsWithFreeOrFullSpotsAndOneAboutToStart() {
        when(workshops.count()).thenReturn(0L);

        demo.run(null);

        ArgumentCaptor<List<Workshop>> saved = ArgumentCaptor.forClass(List.class);
        verify(lock).acquireForTransaction("demo-data:workshops");
        verify(workshops).saveAll(saved.capture());
        List<Workshop> seeds = saved.getValue();
        assertThat(seeds).extracting(Workshop::getId).doesNotHaveDuplicates();
        assertThat(seeds).allSatisfy(workshop -> {
            assertThat(workshop.getOccupied()).isBetween(0, workshop.getCapacity());
            assertThat(workshop.hasEndedAt(NOW)).isFalse();
            assertThat(workshop.getPublishedAt()).isBefore(NOW);
        });
        assertThat(seeds).as("un taller lleno para mostrar la lista llena").anyMatch(workshop -> !workshop.hasFreeSpot());
        assertThat(seeds).as("uno que empieza pronto para probar la sala en vivo")
                .anyMatch(workshop -> workshop.getStartsAt().equals(NOW.plus(Duration.ofMinutes(45))));
        assertThat(seeds).filteredOn(workshop -> workshop.getOrganization().equals("Órbita Labs S.A.S.")).hasSize(1);
    }

    @Test
    void doesNothingWhenWorkshopsAlreadyExist() {
        when(workshops.count()).thenReturn(8L);

        demo.run(null);

        verify(workshops, never()).saveAll(any());
    }
}
