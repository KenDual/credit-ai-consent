package com.demo.credit.service;

import com.demo.credit.service.dto.ScoreResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class ModelClient {

    private final RestTemplate restTemplate;

    @Value("${model.baseUrl}")
    private String baseUrl;

    public ModelClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ScoreResponse score(Map<String, Object> features) {
        Map<String, Object> body = new HashMap<>();
        body.put("features", features);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<ScoreResponse> resp = restTemplate.postForEntity(baseUrl + "/score", req, ScoreResponse.class);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> getFeatureSchema() {
        var resp = restTemplate.getForEntity(baseUrl + "/schema/features", java.util.Map.class);
        var body = (java.util.Map<String, Object>) resp.getBody();
        return (java.util.List<String>) body.getOrDefault("features", java.util.List.of());
    }
}
