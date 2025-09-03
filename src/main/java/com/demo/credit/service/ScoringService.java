package com.demo.credit.service;

import com.demo.credit.repository.ApplicationRepository;
import com.demo.credit.repository.ConsentRepository;
import com.demo.credit.repository.ScoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        // 1) Lấy thông tin hồ sơ để xác thực consent thuộc hồ sơ
        var app = applicationRepository.detail(applicationId)
                .orElseThrow(() -> new IllegalStateException("Application not found"));
        if (!app.consentId().equals(consentId)) {
            throw new IllegalStateException("ConsentId does not match application");
        }

        // 2) Consent ACTIVE + chưa hết hạn
        var cons = consentRepository.findActive(consentId)
                .orElseThrow(() -> new IllegalStateException("Consent not active or expired"));

        // 3) Kiểm tra txHash
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalStateException("txHash is required");
        }
        if (cons.lastTxHash() != null && !txHash.equalsIgnoreCase(cons.lastTxHash())) {
            throw new IllegalStateException("txHash does not match latest consent proof");
        }
        // (Optional) xác minh thêm qua ledger service — không chặn nếu fail mạng
        ledgerClient.verifyConsentTx(consentId, txHash);

        // 4) Gọi Model API để chấm điểm
        var modelResp = modelApiClient.callScore(features);
        if (modelResp == null || modelResp.getScore() == null || modelResp.getPd() == null) {
            throw new IllegalStateException("Model API returned invalid payload");
        }

        // 5) Chuẩn hoá decision (fallback nếu null)
        String decision = (modelResp.getDecision() == null || modelResp.getDecision().isBlank())
                ? fallbackDecision(modelResp.getScore())
                : modelResp.getDecision().toUpperCase(Locale.ROOT);

        // 6) top reasons -> JSON
        String topReasonsJson;
        try {
            topReasonsJson = objectMapper.writeValueAsString(modelResp.getShapTopK());
        } catch (Exception e) {
            topReasonsJson = "[]";
        }

        // 7) Lưu score vào DB (SP tự update status ứng dụng)
        scoreRepository.saveScore(
                applicationId,
                consentId,
                txHash,
                modelResp.getModelVersion(),
                modelResp.getFeatureSchemaVersion(),
                modelResp.getScore(),
                modelResp.getPd(),
                decision,
                topReasonsJson
        );

        // 8) Trả kết quả gọn cho controller
        return new SavedScore(
                modelResp.getScore(),
                modelResp.getPd(),
                decision,
                modelResp.getModelVersion(),
                modelResp.getFeatureSchemaVersion(),
                modelResp.getShapTopK()
        );
    }

    private String fallbackDecision(int score) {
        if (score >= 700) return "APPROVE";
        if (score >= 650) return "REVIEW";
        return "REJECT";
    }

    // Payload trả về cho controller
    public record SavedScore(
            Integer score, Double pd, String decision,
            String modelVersion, String featureSchemaVersion,
            String[] topReasons
    ) {}
}
