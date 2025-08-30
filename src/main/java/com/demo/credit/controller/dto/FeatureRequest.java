package com.demo.credit.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class FeatureRequest {
    public String userId;  // optional
    public String consentId;
    @NotNull
    public Map<String, Object> features;
}
