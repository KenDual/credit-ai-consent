package com.demo.credit.service.features.providers;

import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.features.FeatureProvider;

import java.util.*;

public class EcomFeaturesProvider implements FeatureProvider {
    private static final Set<String> REQ = Set.of("ecom");

    @Override public Set<String> requiredScopes() { return REQ; }

    @Override
    public Map<String, Object> compute(RawSignalsRequest in) {
        Map<String, Object> f = new LinkedHashMap<>();
        var events = (in != null ? in.ecom : null);
        int n = (events == null ? 0 : events.size());
        if (n == 0) {
            f.put("ecom_cat_fashion_ratio", 0.0);
            return f;
        }
        int fashion = 0;
        for (var e : events) {
            String c = (e != null && e.category != null) ? e.category.toLowerCase() : "";
            if (c.contains("fashion")) fashion++;
        }
        f.put("ecom_cat_fashion_ratio", (double) fashion / n);
        return f;
    }
}
