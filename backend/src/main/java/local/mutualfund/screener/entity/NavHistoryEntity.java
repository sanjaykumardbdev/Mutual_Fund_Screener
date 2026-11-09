package local.mutualfund.screener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "MUTUAL_FUND_NAV_HISTORY")
public class NavHistoryEntity {

    @Id
    @Column(name = "NAV_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SCHEME_ID", nullable = false)
    private FundSchemeEntity scheme;

    @Column(name = "NAV_DATE", nullable = false)
    private LocalDate navDate;

    @Column(name = "NAV_VALUE", nullable = false)
    private BigDecimal navValue;

    @Column(name = "PROVIDER_NAME", nullable = false)
    private String providerName;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "RETRIEVED_AT", nullable = false)
    private LocalDateTime retrievedAt;

    protected NavHistoryEntity() {
    }

    public Long getId() {
        return id;
    }

    public FundSchemeEntity getScheme() {
        return scheme;
    }

    public LocalDate getNavDate() {
        return navDate;
    }

    public BigDecimal getNavValue() {
        return navValue;
    }

    public String getProviderName() {
        return providerName;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getRetrievedAt() {
        return retrievedAt;
    }
}
