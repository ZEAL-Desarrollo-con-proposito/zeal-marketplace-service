package co.zeal.marketplace.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {

    Optional<Project> findByIdAndOrganization(String id, String organization);

    @Query("""
            SELECT p FROM Project p
            WHERE p.organization = :organization AND p.closesAt > :now
            ORDER BY p.closesAt
            """)
    List<Project> findOpen(@Param("organization") String organization, @Param("now") Instant now);

    List<Project> findByOrganizationOrderByPublishedAtDesc(String organization);
}
