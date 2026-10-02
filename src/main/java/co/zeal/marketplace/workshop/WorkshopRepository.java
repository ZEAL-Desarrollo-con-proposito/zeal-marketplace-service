package co.zeal.marketplace.workshop;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkshopRepository extends JpaRepository<Workshop, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workshop w WHERE w.id = :id")
    Optional<Workshop> findByIdWithLock(@Param("id") Long id);
}
