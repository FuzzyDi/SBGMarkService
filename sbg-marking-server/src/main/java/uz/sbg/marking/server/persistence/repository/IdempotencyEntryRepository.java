package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import uz.sbg.marking.server.persistence.entity.IdempotencyEntryEntity;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyEntryRepository extends JpaRepository<IdempotencyEntryEntity, Long> {
    Optional<IdempotencyEntryEntity> findByRouteAndOperationId(String route, String operationId);

    @Modifying
    @Transactional
    long deleteByUpdatedAtBefore(Instant threshold);
}
