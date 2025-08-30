package com.demo.credit.service.features;

import com.demo.credit.controller.dto.RawSignalsRequest;
import java.util.Map;
import java.util.Set;

public interface FeatureProvider {

    Set<String> requiredScopes();
    Map<String, Object> compute(RawSignalsRequest input);
}
