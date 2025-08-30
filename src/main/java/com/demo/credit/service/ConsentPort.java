package com.demo.credit.service;

import com.demo.credit.controller.dto.ConsentDtos.*;

public interface ConsentPort {
    GiveResponse   give(GiveRequest req);
    RevokeResponse revoke(RevokeRequest req);
    StatusResponse status(String consentId);
    ProofResponse  proof(String consentId);
}
