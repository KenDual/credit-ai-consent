package com.demo.credit.service;

import com.demo.credit.repository.ApplicationRepository;
import com.demo.credit.repository.ConsentRepository;
import com.demo.credit.repository.ScoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ApplicationRepository applicationRepository;
    private final ConsentRepository consentRepository;
    private final ScoreRepository scoreRepository;

    private final ModelApiClient modelApiClient;
    private final LedgerClient ledgerClient;
    private final ObjectMapper objectMapper;

    public SavedScore score(UUID applicationId, String consentId, String txHash, Map<String, Double> features) {
        
        var app = applicationRepository.detail(applicationId)
                .orElseThrow(() -> new IllegalStateException("Application not found"));
        if (!app.consentId().equals(consentId)) {
            throw new IllegalStateException("ConsentId does not match application");
        }

        var cons = consentRepository.findActive(consentId)
                .orElseThrow(() -> new IllegalStateException("Consent not active or expired"));

        if (txHash == null || txHash.isBlank()) {
            throw new IllegalStateException("txHash is required");
        }
        if (cons.lastTxHash() != null && !txHash.equalsIgnoreCase(cons.lastTxHash())) {
            throw new IllegalStateException("txHash does not match latest consent proof");
        }
        ledgerClient.verifyConsentTx(consentId, txHash);

        var modelResp = modelApiClient.callScore(features);
        if (modelResp == null || modelResp.getScore() == null || modelResp.getPd() == null) {
            throw new IllegalStateException("Model API returned invalid payload");
        }

        String decision = (modelResp.getDecision() == null || modelResp.getDecision().isBlank())
                ? fallbackDecision(modelResp.getScore())
                : modelResp.getDecision().toUpperCase(Locale.ROOT);

        String topReasonsJson;
        Object reasonsObj = modelResp.getReasons();
        try {
            topReasonsJson = objectMapper.writeValueAsString(reasonsObj == null ? List.of() : reasonsObj);
        } catch (Exception e) {
            topReasonsJson = "[]";
        }

        scoreRepository.saveScore(
                applicationId,
                consentId,
                txHash,
                modelResp.getModelVersion() != null ? modelResp.getModelVersion() : "m1",
                modelResp.getFeatureSchemaVersion() != null ? modelResp.getFeatureSchemaVersion() : "v1",
                modelResp.getScore(),
                modelResp.getPd(),
                decision,
                topReasonsJson
        );

        return new SavedScore(
                modelResp.getScore(),
                modelResp.getPd(),
                decision,
                modelResp.getModelVersion() != null ? modelResp.getModelVersion() : "m1",
                modelResp.getFeatureSchemaVersion() != null ? modelResp.getFeatureSchemaVersion() : "v1",
                toReasonStrings(reasonsObj)
        );
    }

    private String fallbackDecision(int score) {
        if (score >= 700) return "APPROVE";
        if (score >= 650) return "REVIEW";
        return "REJECT";
    }

    @SuppressWarnings("unchecked")
    private String[] toReasonStrings(Object reasons) {
        try {
            if (reasons == null) return new String[0];
            if (reasons instanceof List<?> list) {
                var out = new ArrayList<String>();
                for (Object it : list) {
                    if (it == null) continue;
                    if (it instanceof String s) {
                        out.add(s);
                    } else if (it instanceof Map<?, ?> m) {
                        Object feat = m.get("feature");
                        Object impact = m.get("impact");
                        if (feat != null && impact != null) {
                            out.add(feat + ":" + impact);
                        } else {
                            out.add(objectMapper.writeValueAsString(m));
                        }
                    } else {
                        out.add(String.valueOf(it));
                    }
                }
                return out.toArray(new String[0]);
            }
            if (reasons instanceof String s) return new String[]{s};
            return new String[0];
        } catch (Exception e) {
            return new String[0];
        }
    }

    public record SavedScore(
            Integer score, Double pd, String decision,
            String modelVersion, String featureSchemaVersion,
            String[] topReasons
    ) {}
}
