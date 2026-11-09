package local.mutualfund.screener.repository;

import local.mutualfund.screener.entity.NavHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NavHistoryRepository extends JpaRepository<NavHistoryEntity, Long>,
        JpaSpecificationExecutor<NavHistoryEntity> {
}
