package com.demo.credit.repository;

import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ApplicationRepository {
    private final Map<String, LoanApplication> store = new ConcurrentHashMap<>();

    public LoanApplication save(LoanApplication app) {
        app.updatedAt = System.currentTimeMillis();
        store.put(app.appId, app);
        return app;
    }

    public Optional<LoanApplication> find(String appId) {
        return Optional.ofNullable(store.get(appId));
    }

    public List<LoanApplication> list() {
        return new ArrayList<>(store.values());
    }
}
