package co.zeal.marketplace.project;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {

    /**
     * Reto C-1: SELECT ... FOR UPDATE. Las postulaciones concurrentes a una convocatoria se
     * atienden una a la vez, así {@code occupied} nunca supera {@code capacity}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.organization = :organization")
    Optional<Project> findForUpdate(@Param("id") String id, @Param("organization") String organization);

    Optional<Project> findByIdAndOrganization(String id, String organization);

    @Query("""
            SELECT p FROM Project p
            WHERE p.organization = :organization AND p.closesAt > :now
            ORDER BY p.closesAt
            """)
    List<Project> findOpen(@Param("organization") String organization, @Param("now") Instant now);

    List<Project> findByOrganizationOrderByPublishedAtDesc(String organization);
}
