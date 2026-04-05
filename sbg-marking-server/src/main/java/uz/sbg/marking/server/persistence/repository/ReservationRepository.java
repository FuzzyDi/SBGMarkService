package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sbg.marking.server.persistence.entity.ReservationEntity;

public interface ReservationRepository extends JpaRepository<ReservationEntity, String> {
}
