package com.demo.credit.controller;

import com.demo.credit.service.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/score")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoringService scoringService;

    // Body không dùng DTO — nhận map với keys: consentId, txHash, features
    @PostMapping("/{appId}")
    public ScoringService.SavedScore score(
            @PathVariable("appId") UUID appId,
            @RequestBody Map<String, Object> body
    ) {
        String consentId = (String) body.get("consentId");
        String txHash    = (String) body.get("txHash");
        @SuppressWarnings("unchecked")
        Map<String, Double> features = (Map<String, Double>) body.get("features");
        return scoringService.score(appId, consentId, txHash, features);
    }
}
