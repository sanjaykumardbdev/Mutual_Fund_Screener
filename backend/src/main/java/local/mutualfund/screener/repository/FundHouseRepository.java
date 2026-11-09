package local.mutualfund.screener.repository;

import java.util.List;
import local.mutualfund.screener.entity.FundHouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundHouseRepository extends JpaRepository<FundHouseEntity, Long> {

    List<FundHouseEntity> findAllByActiveOrderByNameAsc(Integer active);
}
