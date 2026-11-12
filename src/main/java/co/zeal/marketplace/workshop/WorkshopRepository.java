package co.zeal.marketplace.workshop;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkshopRepository extends JpaRepository<Workshop, String> {

    /** R2-8: el taller solo si es de la empresa de quien pregunta (filtro en la consulta, SEG-3). */
    Optional<Workshop> findByIdAndOrganization(String id, String organization);

    /** Reto C-1 en talleres: SELECT ... FOR UPDATE serializa las inscripciones concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workshop w WHERE w.id = :id AND w.organization = :organization")
    Optional<Workshop> findForUpdate(@Param("id") String id, @Param("organization") String organization);

    @Query("""
            SELECT w FROM Workshop w
            WHERE w.organization = :organization AND w.startsAt > :now
            ORDER BY w.startsAt
            """)
    List<Workshop> findUpcoming(@Param("organization") String organization, @Param("now") Instant now);

    List<Workshop> findByOrganizationOrderByPublishedAtDesc(String organization);
}
