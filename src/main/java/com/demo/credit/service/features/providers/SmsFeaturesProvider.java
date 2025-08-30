package com.demo.credit.service.features.providers;

import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.features.FeatureProvider;

import java.util.*;

public class SmsFeaturesProvider implements FeatureProvider {
    private static final Set<String> REQ = Set.of("sms");

    @Override public Set<String> requiredScopes() { return REQ; }

    @Override
    public Map<String, Object> compute(RawSignalsRequest in) {
        Map<String, Object> f = new LinkedHashMap<>();
        var sms = (in != null ? in.sms : null);
        int total = (sms == null ? 0 : sms.size());
        f.put("sms_count", total);

        if (total > 0) {
            int fin = 0;
            for (var s : sms) {
                String t = (s != null && s.text != null) ? s.text.toLowerCase() : "";
                if (t.contains("loan") || t.contains("repay") || t.contains("pay") || t.contains("debt"))
                    fin++;
            }
            f.put("sms_fin_ratio", (double) fin / total);
        } else {
            f.put("sms_fin_ratio", 0.0);
        }
        return f;
    }
}
