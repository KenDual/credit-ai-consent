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
    private final ConsentPort consent;

    public ScoringController(ModelClient modelClient, ConsentPort consent) {
        this.modelClient = modelClient; this.consent = consent;
    }

    @GetMapping("/model/schema")
    public Map<String, Object> schema() {
        var feats = modelClient.getFeatureSchema();
        Map<String, Object> out = new HashMap<>();
        out.put("count", feats.size());
        out.put("features", feats);
        return out;
    }

    @PostMapping(value = "/score", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ScoreResponse score(@RequestBody FeatureRequest req) {
        if (req.consentId == null || req.consentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ConsentId required");
        }
        var st = consent.status(req.consentId);
        if (st == null || !st.found || !st.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Consent invalid" + (st != null && st.reason != null ? ": " + st.reason : ""));
        }
        return modelClient.score(req.features);
    }
}
