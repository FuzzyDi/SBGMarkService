package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.sbg.marking.server.persistence.entity.AdminAuditEventEntity;

import java.util.List;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEventEntity, Long> {
    default List<AdminAuditEventEntity> findAllByNewest() {
        return findAll(Sort.by(Sort.Direction.DESC, "eventTs").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
