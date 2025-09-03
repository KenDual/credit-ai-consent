package com.demo.credit.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ScoreRepository {

    private final DataSource dataSource;

    public void saveScore(UUID applicationId, String consentId, String txHash,
                          String modelVersion, String featureSchemaVersion,
                          int score, double pd, String decisionUpper, String topReasonsJson) {

        String dec = decisionUpper == null ? null : decisionUpper.toUpperCase(Locale.ROOT);

        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withSchemaName("core")
                .withProcedureName("sp_SaveScore")
                .declareParameters(
                        new SqlParameter("application_id", Types.VARCHAR),
                        new SqlParameter("consent_id", Types.CHAR),
                        new SqlParameter("tx_hash", Types.CHAR),
                        new SqlParameter("model_version", Types.VARCHAR),
                        new SqlParameter("feature_schema_version", Types.VARCHAR),
                        new SqlParameter("score", Types.INTEGER),
                        new SqlParameter("pd", Types.DECIMAL),
                        new SqlParameter("decision", Types.VARCHAR),
                        new SqlParameter("top_reasons", Types.NVARCHAR)
                );

        call.execute(Map.of(
                "application_id", applicationId.toString(),
                "consent_id", consentId,
                "tx_hash",      txHash,
                "model_version", modelVersion,
                "feature_schema_version", featureSchemaVersion,
                "score", score,
                "pd", BigDecimal.valueOf(pd),
                "decision", dec,
                "top_reasons", topReasonsJson
        ));
    }
}
