package com.demo.credit.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ConsentRepository {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public void upsert(String consentId, UUID applicantId, String scopesJson,
                       LocalDateTime expiry, String status, String lastTxHash, String subjectPubKey) {
        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withSchemaName("core")
                .withProcedureName("sp_UpsertConsent")
                .declareParameters(
                        new SqlParameter("consent_id", Types.CHAR),
                        new SqlParameter("applicant_id", Types.VARCHAR), // uniqueidentifier as string
                        new SqlParameter("scopes", Types.NVARCHAR),
                        new SqlParameter("expiry", Types.TIMESTAMP),
                        new SqlParameter("status", Types.VARCHAR),
                        new SqlParameter("last_tx_hash", Types.CHAR),
                        new SqlParameter("subject_pubkey", Types.NVARCHAR)
                );

        call.execute(Map.of(
                "consent_id", consentId,
                "applicant_id", applicantId.toString(),
                "scopes", scopesJson,
                "expiry", java.sql.Timestamp.valueOf(expiry),
                "status", status,
                "last_tx_hash", lastTxHash,
                "subject_pubkey", subjectPubKey
        ));
    }

    public Optional<ConsentRow> findActive(String consentId) {
        String sql = """
            SELECT consent_id, applicant_id, scopes, expiry, status, last_tx_hash, subject_pubkey, created_at, updated_at
            FROM core.Consents
            WHERE consent_id = ? AND status = 'ACTIVE' AND expiry > SYSUTCDATETIME()
        """;
        return jdbc.query(sql, rm(), consentId).stream().findFirst();
    }

    private RowMapper<ConsentRow> rm() {
        return (rs, i) -> new ConsentRow(
                rs.getString("consent_id"),
                UUID.fromString(rs.getString("applicant_id")),
                rs.getString("scopes"),
                rs.getTimestamp("expiry").toLocalDateTime(),
                rs.getString("status"),
                rs.getString("last_tx_hash"),
                rs.getString("subject_pubkey")
        );
    }

    public record ConsentRow(
            String consentId, UUID applicantId, String scopesJson,
            LocalDateTime expiry, String status, String lastTxHash, String subjectPubKey
    ) {}
}
