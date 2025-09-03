package com.demo.credit.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ModelApiClient {

    private final RestTemplate restTemplate;

    @Value("${model.baseUrl}")
    private String modelBaseUrl;

    /**
     * Gọi FastAPI /score với payload features.
     * Expect JSON: { "pd":0.12345, "score":720, "decision":"APPROVE",
     *                "shapTopK":["reason1","reason2","reason3"],
     *                "model_version":"v1", "feature_schema_version":"fs1" }
     */
    public ScoreResult callScore(Map<String, Double> features) {
        try {
            URI uri = URI.create(modelBaseUrl + "/score");
            var req = RequestEntity
                    .post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("features", features));
            ResponseEntity<ScoreResult> resp = restTemplate.exchange(req, ScoreResult.class);
            return resp.getBody();
        } catch (Exception ex) {
            throw new RuntimeException("Model API call failed: " + ex.getMessage(), ex);
        }
    }

    // DTO nội bộ client (bạn có thể thay bằng dto.ScoreResult nếu đã tạo)
    @Data
    public static class ScoreResult {
        private Double pd;
        private Integer score;
        private String decision;
        @JsonProperty("shapTopK")
        private String[] shapTopK;
        @JsonProperty("model_version")
        private String modelVersion;
        @JsonProperty("feature_schema_version")
        private String featureSchemaVersion;
    }
}
