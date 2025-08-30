package com.demo.credit.controller;

import com.demo.credit.controller.dto.ConsentDtos.*;
import com.demo.credit.service.ConsentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/consent")
public class ConsentController {

    private final ConsentService consent;

    public ConsentController(ConsentService consent) { this.consent = consent; }

    @GetMapping("/status")
    public StatusRes status(@RequestParam String userId,
                            @RequestParam(defaultValue = "credit_scoring") String purpose) {
        try {
            boolean ok = consent.hasConsent(userId, purpose);
            return new StatusRes(userId, purpose, ok);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "chain call failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/grant")
    public TxRes grant(@RequestBody GrantReq req) {
        if (req.userId == null || req.purpose == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId & purpose are required");
        }
        long ttl = (req.ttlSec != null ? req.ttlSec : 7L*24*3600);
        try {
            String tx = consent.recordConsent(req.userId, req.purpose, ttl);
            return new TxRes(tx);
        } catch (IllegalStateException ise) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, ise.getMessage(), ise);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "grant failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/revoke")
    public TxRes revoke(@RequestBody RevokeReq req) {
        if (req.userId == null || req.purpose == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId & purpose are required");
        }
        try {
            String tx = consent.revokeConsent(req.userId, req.purpose);
            return new TxRes(tx);
        } catch (IllegalStateException ise) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, ise.getMessage(), ise);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "revoke failed: " + e.getMessage(), e);
        }
    }
}
