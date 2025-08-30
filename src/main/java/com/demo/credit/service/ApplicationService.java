package com.demo.credit.service;

import com.demo.credit.controller.dto.ConsentDtos;
import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.repository.ApplicationRepository;
import com.demo.credit.repository.LoanApplication;
import com.demo.credit.service.dto.ScoreResponse;
import com.demo.credit.service.features.FeatureBuilderService;
import com.demo.credit.service.features.FeatureProvider;
import com.demo.credit.service.features.providers.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final int MAX_SMS      = 2000;
    private static final int MAX_CONTACTS = 5000;
    private static final int MAX_EMAILS   = 2000;
    private static final int MAX_ECOM     = 5000;
    private static final int MAX_WEB      = 5000;

    private final ApplicationRepository repo;
    private final ConsentPort consent;
    private final ModelClient model;

    public ApplicationService(ApplicationRepository repo, ConsentPort consent, ModelClient model) {
        this.repo = repo; this.consent = consent; this.model = model;
    }

    // -----------------------------
    // Lifecycle hồ sơ
    // -----------------------------
    public LoanApplication create(String userId, String consentId) {
        var st = consent.status(consentId);
        if (st == null || !st.found || !st.active) {
            throw new IllegalStateException("Consent invalid" + (st != null && st.reason != null ? ": " + st.reason : ""));
        }

        LoanApplication app = new LoanApplication();
        app.appId = UUID.randomUUID().toString();
        app.userId = (userId == null || userId.isBlank()) ? "anon" : userId;

        app.consentId = consentId;
        app.scope = st.scope;
        app.expiry = st.expiry;
        app.subjectPubKey = st.subjectPubKey;
        app.lastTxHash = st.txHash;
        app.consentActive = st.active;
        app.consentReason = st.reason;

        long now = System.currentTimeMillis();
        app.createdAt = now; app.updatedAt = now;

        return repo.save(app);
    }

    public List<LoanApplication> list() {
        return repo.list();
    }

    public LoanApplication get(String appId) {
        return repo.find(appId).orElseThrow(() -> new NoSuchElementException("app not found"));
    }

    public LoanApplication refreshConsent(String appId) {
        var app = get(appId);
        var st = consent.status(app.consentId);
        if (st != null) {
            app.scope = st.scope;
            app.expiry = st.expiry;
            app.subjectPubKey = st.subjectPubKey;
            app.lastTxHash = st.txHash;
            app.consentActive = st.active;
            app.consentReason = st.reason;
            repo.save(app);
        }
        return app;
    }

    // -----------------------------
    // Scoring từ features đã build sẵn (giữ nguyên)
    // -----------------------------
    public ScoreResponse score(String appId, Map<String, Object> features) {
        var app = refreshConsent(appId);
        if (!app.consentActive) {
            throw new IllegalStateException("Consent invalid" + (app.consentReason != null ? ": " + app.consentReason : ""));
        }

        ScoreResponse r = model.score(features);
        persistScore(app, r);
        return r;
    }

    // -----------------------------
    // Scoring từ raw signals (Feature Builder + scope enforcement + size guard)
    // -----------------------------
    public ScoreResponse scoreFromRaw(String appId, RawSignalsRequest raw) {
        var app = refreshConsent(appId);
        if (!app.consentActive) {
            throw new IllegalStateException("Consent invalid" + (app.consentReason != null ? ": " + app.consentReason : ""));
        }

        // 1) Kiểm tra input size (tránh payload quá lớn)
        enforceSizeLimits(raw);

        // 2) Chặn dữ liệu ngoài scope (422 ở controller)
        Set<String> allowed = parseScopes(app.scope);
        Set<String> present = presentScopes(raw);
        Set<String> disallowed = new HashSet<>(present);
        disallowed.removeAll(allowed);
        if (!disallowed.isEmpty()) {
            // dùng IllegalArgumentException để controller map -> 422
            throw new IllegalArgumentException("Disallowed signals: " + String.join(",", disallowed)
                    + " (allowed: " + String.join(",", allowed) + ")");
        }

        // 3) Build features theo scope
        Map<String, Object> feats = featureBuilder().build(raw, app.scope);

        // 4) Gọi model & lưu kết quả; không lưu dữ liệu thô
        ScoreResponse r = model.score(feats);
        persistScore(app, r);
        return r;
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private FeatureBuilderService featureBuilder() {
        List<FeatureProvider> providers = List.of(
                new SmsFeaturesProvider(),
                new ContactsFeaturesProvider(),
                new EmailFeaturesProvider(),
                new EcomFeaturesProvider(),
                new WebFeaturesProvider()
        );
        return new FeatureBuilderService(providers);
    }

    private void persistScore(LoanApplication app, ScoreResponse r) {
        app.pd = r.getPd();
        app.score = r.getScore();
        app.decision = r.getDecision();
        app.threshold = r.getThreshold();
        app.shapTopK = r.getShapTopK();
        repo.save(app);
    }

    private static Set<String> parseScopes(String scopesStr) {
        if (scopesStr == null || scopesStr.isBlank()) return Set.of();
        return Arrays.stream(scopesStr.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Trả về các loại dữ liệu thô đang được gửi lên (chỉ tính loại có phần tử > 0) */
    private static Set<String> presentScopes(RawSignalsRequest in) {
        Set<String> s = new LinkedHashSet<>();
        if (in == null) return s;
        if (in.sms      != null && !in.sms.isEmpty())      s.add("sms");
        if (in.contacts != null && !in.contacts.isEmpty()) s.add("contacts");
        if (in.emails   != null && !in.emails.isEmpty())   s.add("email");
        if (in.ecom     != null && !in.ecom.isEmpty())     s.add("ecom");
        if (in.web      != null && !in.web.isEmpty())      s.add("web");
        return s;
    }

    /** Giới hạn kích thước từng danh sách để tránh lạm dụng & bảo vệ dịch vụ */
    private static void enforceSizeLimits(RawSignalsRequest in) {
        if (in == null) return;
        if (in.sms      != null && in.sms.size()      > MAX_SMS)      throw new IllegalArgumentException("sms list too large");
        if (in.contacts != null && in.contacts.size() > MAX_CONTACTS) throw new IllegalArgumentException("contacts list too large");
        if (in.emails   != null && in.emails.size()   > MAX_EMAILS)   throw new IllegalArgumentException("emails list too large");
        if (in.ecom     != null && in.ecom.size()     > MAX_ECOM)     throw new IllegalArgumentException("ecom list too large");
        if (in.web      != null && in.web.size()      > MAX_WEB)      throw new IllegalArgumentException("web list too large");
    }

    // -----------------------------
    // Consent ops theo app
    // -----------------------------
    public ConsentDtos.RevokeResponse revoke(String appId) {
        var app = get(appId);
        ConsentDtos.RevokeRequest req = new ConsentDtos.RevokeRequest();
        req.consentId = app.consentId; // INSECURE_LEDGER=1: pubKey/signature có thể để trống
        var res = consent.revoke(req);
        refreshConsent(appId);
        return res;
    }

    public ConsentDtos.ProofResponse proof(String appId) {
        var app = get(appId);
        return consent.proof(app.consentId);
    }
}
