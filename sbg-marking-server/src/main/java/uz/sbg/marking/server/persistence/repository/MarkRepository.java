package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sbg.marking.server.persistence.entity.MarkEntity;

public interface MarkRepository extends JpaRepository<MarkEntity, String> {
}
