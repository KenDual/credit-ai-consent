package com.demo.credit.controller;

import com.demo.credit.repository.ConsentRepository;
import com.demo.credit.service.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @PostMapping
    public Map<String, Object> upsert(@RequestBody Map<String, String> body) {
        String consentId   = body.get("consentId");
        UUID applicantId   = UUID.fromString(body.get("applicantId"));
        String scopesJson  = body.getOrDefault("scopesJson", "{}");
        String expiryStr   = body.get("expiry"); // ISO8601, ví dụ "2030-01-01T00:00:00"
        LocalDateTime expiry = LocalDateTime.parse(expiryStr);
        String status      = body.getOrDefault("status", "ACTIVE");
        String lastTxHash  = body.getOrDefault("lastTxHash", "");
        String subjectKey  = body.getOrDefault("subjectPubKey", "");

        consentService.upsert(consentId, applicantId, scopesJson, expiry, status, lastTxHash, subjectKey);
        return Map.of("ok", true, "consentId", consentId);
    }

    @GetMapping("/{consentId}")
    public Map<String, Object> get(@PathVariable String consentId) {
        Optional<ConsentRepository.ConsentRow> row = consentService.findActive(consentId);
        return row.<Map<String, Object>>map(r -> Map.of(
                    "consentId", r.consentId(),
                    "applicantId", r.applicantId(),
                    "status", r.status(),
                    "expiry", r.expiry(),
                    "lastTxHash", r.lastTxHash(),
                    "active", true
                ))
                .orElseGet(() -> Map.of("consentId", consentId, "active", false));
    }
}
