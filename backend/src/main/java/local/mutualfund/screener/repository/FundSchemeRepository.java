package local.mutualfund.screener.repository;

import local.mutualfund.screener.entity.FundSchemeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FundSchemeRepository extends JpaRepository<FundSchemeEntity, Long>,
        JpaSpecificationExecutor<FundSchemeEntity> {
}
