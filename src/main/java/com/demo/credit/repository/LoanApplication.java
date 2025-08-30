package com.demo.credit.repository;

import java.util.List;

public class LoanApplication {
    public String appId;
    public String userId;

    // Consent
    public String consentId;
    public String scope;            // scopes string từ GIVE
    public Long   expiry;           // epoch seconds
    public String subjectPubKey;
    public String lastTxHash;
    public boolean consentActive;
    public String  consentReason;   // null | "expired" | "revoked"

    // Scoring result
    public Double pd;
    public Integer score;
    public String decision;
    public Double threshold;
    public List<com.demo.credit.service.dto.ScoreResponse.ShapItem> shapTopK;

    // Audit
    public long createdAt;
    public long updatedAt;
}
