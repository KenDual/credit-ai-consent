package com.demo.credit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final RestTemplate restTemplate;

    @Value("${ledger.baseUrl:}")
    private String ledgerBaseUrl;

    /** MVP: gọi GET /verify?consentId=&txHash=  (nếu service của bạn khác path thì đổi lại).
     *  Nếu lỗi mạng/404 -> logWarning và trả false (không chặn flow — DB check vẫn là chính). */
    public boolean verifyConsentTx(String consentId, String txHash) {
        if (ledgerBaseUrl == null || ledgerBaseUrl.isBlank()) {
            log.warn("Ledger base URL missing; skip remote verify");
            return false;
        }
        try {
            URI uri = URI.create(ledgerBaseUrl + "/verify?consentId=" + consentId + "&txHash=" + txHash);
            ResponseEntity<Map> resp = restTemplate.exchange(RequestEntity.get(uri).build(), Map.class);
            Object valid = resp.getBody() == null ? null : resp.getBody().get("valid");
            return Boolean.TRUE.equals(valid);
        } catch (Exception ex) {
            log.warn("Ledger verify failed: {}", ex.toString());
            return false;
        }
    }
}
