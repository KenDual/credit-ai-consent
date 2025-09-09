package com.demo.credit.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    public ScoreResult callScore(Map<String, Double> features) {
        try {
            URI uri = URI.create(modelBaseUrl + "/score");
            var req = RequestEntity
                    .post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("features", features));

            ResponseEntity<ScoreResult> resp = restTemplate.exchange(req, ScoreResult.class);
            ScoreResult body = resp.getBody();
            if (body == null) {
                throw new RuntimeException("Empty body from Model API");
            }
            return body;
        } catch (Exception ex) {
            throw new RuntimeException("Model API call failed: " + ex.getMessage(), ex);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreResult {
        private Integer score;

        @JsonProperty("pd")
        private Double pd;

        private String decision;

        @JsonAlias({"reasons", "top_reasons", "shapTopK", "shap_top_k"})
        private Object reasons;

        @JsonProperty("model_version")
        private String modelVersion;

        @JsonProperty("feature_schema_version")
        private String featureSchemaVersion;
    }
}
