package com.demo.credit.service.features;

import com.demo.credit.controller.dto.RawSignalsRequest;

import java.util.*;
import java.util.stream.Collectors;

/** Orchestrator: chọn providers theo scope, merge tất cả features */
public class FeatureBuilderService {

    private final List<FeatureProvider> providers;

    public FeatureBuilderService(List<FeatureProvider> providers) {
        this.providers = providers;
    }

    /** scopesStr dạng "sms,ecom,web,email" */
    public Map<String, Object> build(RawSignalsRequest input, String scopesStr) {
        Set<String> allowed = Arrays.stream(
                        (scopesStr == null ? "" : scopesStr).split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        Map<String, Object> out = new LinkedHashMap<>();
        for (FeatureProvider p : providers) {
            if (allowed.containsAll(p.requiredScopes())) {
                Map<String, Object> part = safeCompute(p, input);
                if (part != null) out.putAll(part);
            }
        }
        return out;
    }

    private Map<String, Object> safeCompute(FeatureProvider p, RawSignalsRequest in) {
        try { return p.compute(in); }
        catch (Exception e) {
            // fail-closed: provider lỗi -> bỏ qua nhóm features
            return Collections.emptyMap();
        }
    }
}
