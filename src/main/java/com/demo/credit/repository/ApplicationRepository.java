package com.demo.credit.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ApplicationRepository {

    private final DataSource dataSource;

    public UUID create(UUID applicantId, String consentId) {
        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withSchemaName("core")
                .withProcedureName("sp_CreateApplication")
                .declareParameters(
                        new SqlParameter("applicant_id", Types.VARCHAR),
                        new SqlParameter("consent_id", Types.CHAR),
                        new SqlOutParameter("application_id", Types.VARCHAR)
                );

        Map<String, Object> out = call.execute(Map.of(
                "applicant_id", applicantId.toString(),
                "consent_id", consentId
        ));
        return UUID.fromString(String.valueOf(out.get("application_id")));
    }

    public List<ApplicationListItem> list(String status, String q, int page, int size) {
        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withSchemaName("core")
                .withProcedureName("sp_ListApplications")
                .returningResultSet("items", listMapper());

        Map<String, Object> out = call.execute(Map.of(
                "status", status,
                "q", q,
                "page", page,
                "size", size
        ));
        @SuppressWarnings("unchecked")
        List<ApplicationListItem> items = (List<ApplicationListItem>) out.get("items"); // key=alias from returningResultSet
        if (items == null) {
            items = (List<ApplicationListItem>) out.getOrDefault("#result-set-1", List.of());
        }
        return items;
    }

    public Optional<ApplicationDetail> detail(UUID applicationId) {
        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withSchemaName("core")
                .withProcedureName("sp_GetApplicationDetail")
                .returningResultSet("row", detailMapper());

        Map<String, Object> out = call.execute(Map.of("application_id", applicationId.toString()));
        @SuppressWarnings("unchecked")
        List<ApplicationDetail> rows = (List<ApplicationDetail>) out.getOrDefault("row",
                (List<?>) out.getOrDefault("#result-set-1", List.of()));
        return rows.stream().findFirst();
    }

    private RowMapper<ApplicationListItem> listMapper() {
        return (rs, i) -> new ApplicationListItem(
                UUID.fromString(rs.getString("application_id")),
                rs.getString("reference_no"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                UUID.fromString(rs.getString("applicant_id")),
                rs.getString("consent_id"),
                (Integer) rs.getObject("score"),
                (rs.getBigDecimal("pd") == null ? null : rs.getBigDecimal("pd").doubleValue()),
                rs.getString("decision"),
                rs.getTimestamp("scored_at") == null ? null : rs.getTimestamp("scored_at").toLocalDateTime()
        );
    }

    private RowMapper<ApplicationDetail> detailMapper() {
        return (rs, i) -> new ApplicationDetail(
                UUID.fromString(rs.getString("id")),
                rs.getString("reference_no"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                UUID.fromString(rs.getString("applicant_id")),
                rs.getString("consent_id"),

                // latest score (join TOP 1 DESC)
                (UUID) Optional.ofNullable(rs.getString("score_id")).map(UUID::fromString).orElse(null),
                (Integer) rs.getObject("score"),
                rs.getBigDecimal("pd") == null ? null : rs.getBigDecimal("pd").doubleValue(),
                rs.getString("decision"),
                rs.getString("top_reasons"),
                rs.getString("model_version"),
                rs.getString("feature_schema_version"),
                rs.getString("tx_hash"),
                rs.getTimestamp("scored_at") == null ? null : rs.getTimestamp("scored_at").toLocalDateTime(),

                // consent snapshot
                rs.getString("consent_status"),
                rs.getTimestamp("consent_expiry") == null ? null : rs.getTimestamp("consent_expiry").toLocalDateTime(),
                rs.getString("last_tx_hash")
        );
    }

    public record ApplicationListItem(
            UUID applicationId,
            String referenceNo,
            String status,
            LocalDateTime createdAt,
            UUID applicantId,
            String consentId,
            Integer score,
            Double pd,
            String decision,
            LocalDateTime scoredAt
    ) {}

    public record ApplicationDetail(
            UUID id, String referenceNo, String status, LocalDateTime createdAt,
            UUID applicantId, String consentId,
            UUID scoreId, Integer score, Double pd, String decision, String topReasons,
            String modelVersion, String featureSchemaVersion, String txHash, LocalDateTime scoredAt,
            String consentStatus, LocalDateTime consentExpiry, String consentLastTxHash
    ) {}
}
