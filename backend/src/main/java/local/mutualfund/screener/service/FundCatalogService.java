package local.mutualfund.screener.service;

import java.time.LocalDate;
import java.util.List;
import local.mutualfund.screener.dto.FundHouseResponse;
import local.mutualfund.screener.dto.FundSchemeResponse;
import local.mutualfund.screener.dto.NavHistoryResponse;
import local.mutualfund.screener.dto.NavDtos.SchemeDetailsResponse;
import local.mutualfund.screener.entity.FundSchemeEntity;
import local.mutualfund.screener.entity.NavHistoryEntity;
import local.mutualfund.screener.repository.FundHouseRepository;
import local.mutualfund.screener.repository.FundSchemeRepository;
import local.mutualfund.screener.repository.NavHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class FundCatalogService {

    private final FundHouseRepository fundHouseRepository;
    private final FundSchemeRepository fundSchemeRepository;
    private final NavHistoryRepository navHistoryRepository;
    private final NavRefreshService navRefreshService;

    public FundCatalogService(
            FundHouseRepository fundHouseRepository,
            FundSchemeRepository fundSchemeRepository,
            NavHistoryRepository navHistoryRepository,
            NavRefreshService navRefreshService
    ) {
        this.fundHouseRepository = fundHouseRepository;
        this.fundSchemeRepository = fundSchemeRepository;
        this.navHistoryRepository = navHistoryRepository;
        this.navRefreshService = navRefreshService;
    }

    public List<FundHouseResponse> getFundHouses(boolean activeOnly) {
        var houses = activeOnly
                ? fundHouseRepository.findAllByActiveOrderByNameAsc(1)
                : fundHouseRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        return houses.stream().map(FundHouseResponse::from).toList();
    }

    public Page<FundSchemeResponse> getSchemes(
            String houseCode,
            String category,
            String planType,
            String optionType,
            String search,
            int page,
            int size
    ) {
        Specification<FundSchemeEntity> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("active"), 1);

        if (hasText(houseCode)) {
            String normalizedHouseCode = houseCode.trim().toUpperCase();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.join("house").get("code"), normalizedHouseCode));
        }
        if (hasText(category)) {
            String normalizedCategory = category.trim().toUpperCase();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("category"), normalizedCategory));
        }
        if (hasText(planType)) {
            String normalizedPlanType = planType.trim().toUpperCase();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("planType"), normalizedPlanType));
        }
        if (hasText(optionType)) {
            String normalizedOptionType = optionType.trim().toUpperCase();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("optionType"), normalizedOptionType));
        }
        if (hasText(search)) {
            String searchPattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), searchPattern));
        }

        return fundSchemeRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))
        ).map(FundSchemeResponse::from);
    }

    public List<NavHistoryResponse> getNavHistory(Long schemeId, LocalDate fromDate, LocalDate toDate) {
        if (!fundSchemeRepository.existsById(schemeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheme not found");
        }

        Specification<NavHistoryEntity> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("scheme").get("id"), schemeId);

        if (fromDate != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("navDate"), fromDate));
        }
        if (toDate != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("navDate"), toDate));
        }

        return navHistoryRepository.findAll(
                        specification,
                        Sort.by(Sort.Direction.DESC, "navDate")
                ).stream()
                .map(NavHistoryResponse::from)
                .toList();
    }

    public SchemeDetailsResponse getSchemeDetails(long schemeId) {
        return navRefreshService.getSchemeDetails(schemeId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
