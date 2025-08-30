package com.demo.credit.service;

public interface ConsentPort {
    boolean hasConsent(String userId, String purpose) throws Exception;
    String  grantConsent(String userId, String purpose, long ttlSec) throws Exception;
    String  revokeConsent(String userId, String purpose) throws Exception;
}
