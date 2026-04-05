package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sbg.marking.server.persistence.entity.IdempotencyEntryEntity;

import java.util.Optional;

public interface IdempotencyEntryRepository extends JpaRepository<IdempotencyEntryEntity, Long> {
    Optional<IdempotencyEntryEntity> findByRouteAndOperationId(String route, String operationId);
}
