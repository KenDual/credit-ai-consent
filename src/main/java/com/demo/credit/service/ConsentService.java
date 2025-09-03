package com.demo.credit.service;

import com.demo.credit.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;

    public boolean isActive(String consentId) {
        return consentRepository.findActive(consentId).isPresent();
    }

    public Optional<ConsentRepository.ConsentRow> findActive(String consentId) {
        return consentRepository.findActive(consentId);
    }

    public void upsert(String consentId, UUID applicantId, String scopesJson,
                       LocalDateTime expiry, String status, String lastTxHash, String subjectPubKey) {
        consentRepository.upsert(consentId, applicantId, scopesJson, expiry, status, lastTxHash, subjectPubKey);
    }
}
