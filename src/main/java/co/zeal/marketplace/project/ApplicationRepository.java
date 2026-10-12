package co.zeal.marketplace.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, String> {

    Optional<Application> findByProjectIdAndUserId(String projectId, String userId);

    long countByProjectIdAndStatus(String projectId, ApplicationStatus status);

    List<Application> findByUserIdOrderByCreatedAtDesc(String userId);
}
