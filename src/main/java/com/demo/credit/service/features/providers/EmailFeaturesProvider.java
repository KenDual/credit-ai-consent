package com.demo.credit.service.features.providers;

import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.features.FeatureProvider;

import java.util.*;

public class EmailFeaturesProvider implements FeatureProvider {
    private static final Set<String> REQ = Set.of("email");

    @Override public Set<String> requiredScopes() { return REQ; }

    @Override
    public Map<String, Object> compute(RawSignalsRequest in) {
        Map<String, Object> f = new LinkedHashMap<>();
        var emails = (in != null ? in.emails : null);
        int n = (emails == null ? 0 : emails.size());
        if (n == 0) {
            f.put("email_overdue_ratio", 0.0);
            return f;
        }
        int overdue = 0;
        for (var e : emails) {
            if (e != null && e.overdueNotice) overdue++;
        }
        f.put("email_overdue_ratio", (double) overdue / n);
        return f;
    }
}
