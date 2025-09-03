package com.demo.credit.service;

import com.demo.credit.repository.ApplicationRepository;
import com.demo.credit.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ConsentRepository consentRepository;

    public UUID create(UUID applicantId, String consentId) {
        // Pre-check đơn giản: consent phải ACTIVE & thuộc applicant
        var cons = consentRepository.findActive(consentId)
                .orElseThrow(() -> new IllegalStateException("Consent not active or expired"));
        if (!cons.applicantId().equals(applicantId)) {
            throw new IllegalStateException("Consent does not belong to applicant");
        }
        return applicationRepository.create(applicantId, consentId);
    }

    public List<ApplicationRepository.ApplicationListItem> list(String status, String q, int page, int size) {
        return applicationRepository.list(status, q, page, size);
    }

    public ApplicationRepository.ApplicationDetail detail(UUID applicationId) {
        return applicationRepository.detail(applicationId)
                .orElseThrow(() -> new IllegalStateException("Application not found"));
    }
}
