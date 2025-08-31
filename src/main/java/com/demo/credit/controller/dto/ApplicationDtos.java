package com.demo.credit.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.demo.credit.repository.LoanApplication;

import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {}

    public static class CreateRequest {
        public String userId;
        public String consentId;
    }

    public static class AppSummary {
        public String appId;
        public String userId;
        public boolean consentActive;
        public String decision;   // may be null
        public Integer score;     // may be null
        public long updatedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppDetail {
        public String appId;
        public String userId;

        public String consentId;
        public String scope;
        public Long   expiry;
        public String subjectPubKey;
        public String lastTxHash;
        public boolean consentActive;
        public String  consentReason;

        public Double pd;
        public Integer score;
        public String decision;
        public Double threshold;
        public List<com.demo.credit.service.dto.ScoreResponse.ShapItem> shapTopK;

        public long createdAt;
        public long updatedAt;

        public static AppDetail from(LoanApplication a) {
            AppDetail d = new AppDetail();
            d.appId = a.appId; d.userId = a.userId;
            d.consentId = a.consentId; d.scope = a.scope; d.expiry = a.expiry;
            d.subjectPubKey = a.subjectPubKey; d.lastTxHash = a.lastTxHash;
            d.consentActive = a.consentActive; d.consentReason = a.consentReason;
            d.pd = a.pd; d.score = a.score; d.decision = a.decision; d.threshold = a.threshold;
            d.shapTopK = a.shapTopK;
            d.createdAt = a.createdAt; d.updatedAt = a.updatedAt;
            return d;
        }
    }

    public static class AppListResponse {
        public int page;
        public int size;
        public long total;
        public int totalPages;
        public List<AppSummary> items;
    }
}
