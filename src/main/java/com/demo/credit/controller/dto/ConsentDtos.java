package com.demo.credit.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public final class ConsentDtos {
    private ConsentDtos() {}

    // -------- Requests ----------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GiveRequest {
        public String scopes;       // ví dụ "sms,ecom,web,email"
        public long   expiry;       // epoch seconds
        public String dataHash;     // hash tuỳ bạn (vd hash(userId|purpose))
        public String subjectPubKey; // optional nếu INSECURE_LEDGER=1
        public String signature;     // optional nếu INSECURE_LEDGER=1
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RevokeRequest {
        public String consentId;
        public String subjectPubKey; // optional nếu INSECURE
        public String signature;     // optional nếu INSECURE
    }

    // -------- Responses ----------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GiveResponse {
        public boolean ok;
        public String consentId;
        public ChainBlock block;
        public ChainCheck chainCheck;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RevokeResponse {
        public boolean ok;
        public String consentId;
        public ChainBlock block;
        public ChainCheck chainCheck;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusResponse {
        public boolean found;
        public boolean active;
        public String  reason;        // "expired" | "revoked" | null
        public String  scope;         // scopes string
        public Long    expiry;        // epoch seconds
        public String  txHash;        // last tx hash for this consent
        public String  subjectPubKey; // from GIVE
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProofResponse {
        public boolean ok;
        public String error; // nullable
        public List<ChainBlock> blocks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChainCheck {
        public boolean valid;
        public Integer index;
        public String  reason;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChainBlock {
        public int index;
        public String timestamp;
        public String type; // GENESIS|GIVE|REVOKE
        public Payload payload;
        public String subjectPubKey;
        public String signature;
        public String prevHash;
        public String hash;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        public String consentId;
        public String scopes;
        public Long   expiry;
        public String dataHash;
    }
}
