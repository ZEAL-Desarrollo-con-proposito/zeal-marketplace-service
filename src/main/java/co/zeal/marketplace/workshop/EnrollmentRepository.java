package co.zeal.marketplace.workshop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

    Optional<Enrollment> findByWorkshopIdAndUserId(String workshopId, String userId);

    List<Enrollment> findByUserId(String userId);
}
