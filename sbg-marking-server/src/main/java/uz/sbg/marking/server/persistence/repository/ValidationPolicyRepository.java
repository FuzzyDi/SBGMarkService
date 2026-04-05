package uz.sbg.marking.server.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.sbg.marking.server.persistence.entity.ValidationPolicyEntity;

public interface ValidationPolicyRepository extends JpaRepository<ValidationPolicyEntity, Integer> {
}
