package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sbg.marking.server.persistence.entity.HistoryEventEntity;

public interface HistoryEventRepository extends JpaRepository<HistoryEventEntity, Long> {
}
