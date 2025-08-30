package com.demo.credit.controller;

import com.demo.credit.controller.dto.FeatureRequest;
import com.demo.credit.service.ConsentPort;
import com.demo.credit.service.ModelClient;
import com.demo.credit.service.dto.ScoreResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class ScoringController {

    private final ModelClient modelClient;
    private final ConsentPort  consent;

    public ScoringController(ModelClient modelClient, ConsentPort consent) {
        this.modelClient = modelClient;
        this.consent = consent;
    }

    /** 1) Lấy danh sách feature từ AI service (dùng build form/UI) */
    @GetMapping("/model/schema")
    public Map<String, Object> schema() {
        var feats = modelClient.getFeatureSchema();
        Map<String, Object> out = new HashMap<>();
        out.put("count", feats.size());
        out.put("features", feats);
        return out;
    }

    /** 2) Demo GET: mock features -> (check/auto-grant consent) -> gọi model */
    @GetMapping("/demo/score")
    public Map<String, Object> demoScore(
            @RequestParam(defaultValue = "demo001") String userId,
            @RequestParam(defaultValue = "credit_scoring") String purpose) {

        try {
            if (!consent.hasConsent(userId, purpose)) {
                consent.grantConsent(userId, purpose, 7L * 24 * 3600);
            }
        } catch (Exception e) {
            // Demo: nếu chain lỗi vẫn cho đi tiếp, chỉ log cảnh báo
            System.err.println("consent check/grant failed: " + e.getMessage());
        }

        Map<String, Object> f = new HashMap<>();
        f.put("sms_count", 120);
        f.put("contacts_count", 180);
        f.put("email_overdue_ratio", 0.05);
        f.put("sms_fin_ratio", 0.2);
        f.put("ecom_cat_fashion_ratio", 0.1);
        f.put("social_posts_sum", 30);
        f.put("social_likes_sum", 200);
        f.put("emp_formal", 1);
        f.put("region_HCM", 1);
        f.put("monthly_income_vnd", 15_000_000);

        ScoreResponse r = modelClient.score(f);
        Map<String, Object> out = new HashMap<>();
        out.put("userId", userId);
        out.put("pd", r.getPd());
        out.put("score", r.getScore());
        out.put("decision", r.getDecision());
        out.put("threshold", r.getThreshold());
        out.put("topFeatures", r.getShapTopK());
        return out;
    }

    /** 3) POST: nhận features -> (check consent, optional autoGrant) -> gọi model */
    @PostMapping(value = "/score", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ScoreResponse score(
            @RequestBody FeatureRequest req,
            @RequestParam(defaultValue = "credit_scoring") String purpose,
            @RequestParam(defaultValue = "false") boolean autoGrant) {

        String uid = (req.userId != null && !req.userId.isBlank()) ? req.userId : "anon";

        try {
            boolean ok = consent.hasConsent(uid, purpose);
            if (!ok) {
                if (autoGrant) {
                    // Cho demo: tự động grant nếu cho phép qua query param
                    consent.grantConsent(uid, purpose, 7L * 24 * 3600);
                } else {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consent required");
                }
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            // Lỗi khi gọi chain
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "consent check failed: " + e.getMessage(), e);
        }

        return modelClient.score(req.features);
    }
}
