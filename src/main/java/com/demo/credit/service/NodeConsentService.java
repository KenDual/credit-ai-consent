package com.demo.credit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class NodeConsentService implements ConsentPort {

    private final RestTemplate rest;
    private final String base;

    public NodeConsentService(RestTemplate rest, @Value("${ledger.baseUrl}") String baseUrl) {
        this.rest = rest;
        this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
    }

    @Override
    public boolean hasConsent(String userId, String purpose) {
        String id = HashUtil.consentId(userId, purpose);
        String url = String.format("%s/consents/%s/status", base, id);
        Map<?,?> res = rest.getForObject(url, Map.class);
        // server.statusOf(id) nên trả { id, active: true/false, ... }
        Object active = (res != null) ? res.get("active") : null;
        return (active instanceof Boolean) ? (Boolean) active : false;
    }

    @Override
    public String grantConsent(String userId, String purpose, long ttlSec) {
        String id = HashUtil.consentId(userId, purpose);
        String url = base + "/consents/give";

        long expiry = Instant.now().getEpochSecond() + ttlSec;
        Map<String, Object> body = new HashMap<>();
        body.put("scopes", java.util.List.of(purpose));
        body.put("expiry", expiry);
        body.put("dataHash", id);

        // MVP insecure mode: backend sẽ bỏ qua signature/pubKey nếu bật INSECURE_LEDGER=1
        body.put("subjectPubKey", "demo");
        body.put("signature", "demo");

        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(url, new HttpEntity<>(body, h), Map.class);
        Map<?,?> m = resp.getBody();
        // server trả { ok: true, id: "...", ... } -> ta trả lại id
        Object createdId = (m != null) ? m.get("id") : null;
        return (createdId != null) ? createdId.toString() : id;
    }

    @Override
    public String revokeConsent(String userId, String purpose) {
        String id = HashUtil.consentId(userId, purpose);
        String url = base + "/consents/revoke";

        Map<String, Object> body = new HashMap<>();
        body.put("consentId", id);
        body.put("subjectPubKey", "demo");
        body.put("signature", "demo");

        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(url, new HttpEntity<>(body, h), Map.class);
        Map<?,?> m = resp.getBody();
        return (m != null && m.get("ok") instanceof Boolean && (Boolean)m.get("ok")) ? "ok" : "failed";
    }
}
