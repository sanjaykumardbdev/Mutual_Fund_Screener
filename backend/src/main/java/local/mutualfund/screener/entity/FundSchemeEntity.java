package local.mutualfund.screener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "MUTUAL_FUND_SCHEMES")
public class FundSchemeEntity {

    @Id
    @Column(name = "SCHEME_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HOUSE_ID", nullable = false)
    private FundHouseEntity house;

    @Column(name = "SCHEME_CODE", nullable = false)
    private String code;

    @Column(name = "SCHEME_NAME", nullable = false)
    private String name;

    @Column(name = "CATEGORY", nullable = false)
    private String category;

    @Column(name = "PLAN_TYPE", nullable = false)
    private String planType;

    @Column(name = "OPTION_TYPE", nullable = false)
    private String optionType;

    @Column(name = "BENCHMARK")
    private String benchmark;

    @Column(name = "ISIN")
    private String isin;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer active;

    protected FundSchemeEntity() {
    }

    public Long getId() {
        return id;
    }

    public FundHouseEntity getHouse() {
        return house;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getPlanType() {
        return planType;
    }

    public String getOptionType() {
        return optionType;
    }

    public String getBenchmark() {
        return benchmark;
    }

    public String getIsin() {
        return isin;
    }

    public Integer getActive() {
        return active;
    }
}
