package com.demo.credit.service.features.providers;

import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.features.FeatureProvider;

import java.util.*;

public class WebFeaturesProvider implements FeatureProvider {
    private static final Set<String> REQ = Set.of("web");

    @Override public Set<String> requiredScopes() { return REQ; }

    @Override
    public Map<String, Object> compute(RawSignalsRequest in) {
        // MVP: chưa dùng, placeholder để sau mở rộng (vd: web_fin_visit_ratio)
        return new LinkedHashMap<>();
    }
}
