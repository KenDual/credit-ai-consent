package com.demo.credit.controller.dto;

public class ConsentDtos {
    public static class GrantReq {
        public String userId;
        public String purpose;   // ví dụ: "credit_scoring"
        public Long ttlSec;      // ví dụ: 604800 (7 ngày)
    }
    public static class RevokeReq {
        public String userId;
        public String purpose;
    }
    public static class StatusRes {
        public String userId;
        public String purpose;
        public boolean granted;
        public StatusRes(String u, String p, boolean g){ this.userId=u; this.purpose=p; this.granted=g; }
    }
    public static class TxRes {
        public String txHash;
        public TxRes(String h){ this.txHash=h; }
    }
}
