package com.demo.credit.controller;

import com.demo.credit.controller.dto.ConsentDtos.*;
import com.demo.credit.service.ConsentPort;
import com.demo.credit.service.HashUtil;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConsentController {

    private final ConsentPort consent;

    public ConsentController(ConsentPort consent) { this.consent = consent; }

    // Borrower cấp consent -> trả về consentId để gắn vào hồ sơ/app
    @PostMapping("/consents/give")
    public GiveResponse give(@RequestBody Map<String, Object> body) {
        String scopes = (String) body.getOrDefault("scopes", "sms,ecom,web,email");
        long   expiry = ((Number) body.getOrDefault("expiry", Instant.now().getEpochSecond() + 7L*24*3600)).longValue();
        String dataHash = (String) body.getOrDefault("dataHash", HashUtil.consentId("anon","credit_scoring"));
        String pub = (String) body.getOrDefault("subjectPubKey", null);
        String sig = (String) body.getOrDefault("signature", null);

        GiveRequest req = new GiveRequest();
        req.scopes = scopes; req.expiry = expiry; req.dataHash = dataHash; req.subjectPubKey = pub; req.signature = sig;
        return consent.give(req);
    }

    @PostMapping("/consents/{consentId}/revoke")
    public RevokeResponse revoke(@PathVariable String consentId, @RequestBody(required = false) Map<String, String> b) {
        RevokeRequest req = new RevokeRequest();
        req.consentId = consentId;
        req.subjectPubKey = b != null ? b.get("subjectPubKey") : null;
        req.signature = b != null ? b.get("signature") : null;
        return consent.revoke(req);
    }

    @GetMapping("/consents/{consentId}/status")
    public StatusResponse status(@PathVariable String consentId) {
        return consent.status(consentId);
    }

    @GetMapping("/consents/{consentId}/proof")
    public ProofResponse proof(@PathVariable String consentId) {
        return consent.proof(consentId);
    }
}
