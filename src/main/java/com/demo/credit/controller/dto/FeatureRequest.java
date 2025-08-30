package com.demo.credit.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class FeatureRequest {
    public String userId;                 // tuỳ chọn: để log/ghi consent sau này
    @NotNull
    public Map<String, Object> features;
}
