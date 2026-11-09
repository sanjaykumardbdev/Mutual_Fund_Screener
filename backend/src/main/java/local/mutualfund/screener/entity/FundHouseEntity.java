package local.mutualfund.screener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "MUTUAL_FUND_HOUSES")
public class FundHouseEntity {

    @Id
    @Column(name = "HOUSE_ID")
    private Long id;

    @Column(name = "HOUSE_CODE", nullable = false)
    private String code;

    @Column(name = "HOUSE_NAME", nullable = false)
    private String name;

    @Column(name = "COUNTRY_CODE", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String countryCode;

    @Column(name = "WEBSITE_URL")
    private String websiteUrl;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer active;

    protected FundHouseEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public Integer getActive() {
        return active;
    }
}
