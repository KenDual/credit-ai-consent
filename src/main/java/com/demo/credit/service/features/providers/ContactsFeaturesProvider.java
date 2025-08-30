package com.demo.credit.service.features.providers;

import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.features.FeatureProvider;

import java.util.*;

public class ContactsFeaturesProvider implements FeatureProvider {
    private static final Set<String> REQ = Set.of("contacts");

    @Override public Set<String> requiredScopes() { return REQ; }

    @Override
    public Map<String, Object> compute(RawSignalsRequest in) {
        Map<String, Object> f = new LinkedHashMap<>();
        int cnt = (in != null && in.contacts != null) ? in.contacts.size() : 0;
        f.put("contacts_count", cnt);
        return f;
    }
}
