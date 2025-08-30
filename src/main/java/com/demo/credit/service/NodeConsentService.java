package com.demo.credit.service;

import com.demo.credit.controller.dto.ConsentDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NodeConsentService implements ConsentPort {

    private final RestTemplate rest;
    private final String base;

    public NodeConsentService(RestTemplate rest, @Value("${ledger.baseUrl}") String baseUrl) {
        this.rest = rest;
        this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
    }

    @Override
    public GiveResponse give(GiveRequest req) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<GiveResponse> r = rest.postForEntity(base + "/consents/give",
                new HttpEntity<>(req, h), GiveResponse.class);
        return r.getBody();
    }

    @Override
    public RevokeResponse revoke(RevokeRequest req) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RevokeResponse> r = rest.postForEntity(base + "/consents/revoke",
                new HttpEntity<>(req, h), RevokeResponse.class);
        return r.getBody();
    }

    @Override
    public StatusResponse status(String consentId) {
        return rest.getForObject(base + "/consents/{id}/status", StatusResponse.class, consentId);
    }

    @Override
    public ProofResponse proof(String consentId) {
        return rest.getForObject(base + "/consents/{id}/proof",  ProofResponse.class,  consentId);
    }
}
