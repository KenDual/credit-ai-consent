package com.demo.credit.controller;

import com.demo.credit.controller.dto.ApplicationDtos.*;
import com.demo.credit.controller.dto.ConsentDtos;
import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.ApplicationService;
import com.demo.credit.service.dto.ScoreResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.demo.credit.controller.dto.ReasonDtos;
import com.demo.credit.service.ExplainService;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

    private final ApplicationService apps;
    private final ExplainService explain;

    public ApplicationController(ApplicationService apps, ExplainService explain) {
        this.apps = apps;
        this.explain = explain;
    }

    /** Tạo hồ sơ mới từ consentId đã có */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AppDetail create(@RequestBody CreateRequest req) {
        if (req == null || req.consentId == null || req.consentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consentId required");
        }
        try {
            var a = apps.create(req.userId, req.consentId);
            return AppDetail.from(a);
        } catch (IllegalStateException ise) {
            // consent invalid/expired/revoked
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ise.getMessage(), ise);
        }
    }

    /** Danh sách hồ sơ (tối giản) */
    @GetMapping
    public List<AppSummary> list() {
        return apps.list().stream().map(a -> {
            AppSummary s = new AppSummary();
            s.appId = a.appId; s.userId = a.userId; s.consentActive = a.consentActive;
            s.decision = a.decision; s.score = a.score; s.updatedAt = a.updatedAt;
            return s;
        }).collect(Collectors.toList());
    }

    /** Chi tiết hồ sơ (dữ liệu đang lưu) */
    @GetMapping("/{appId}")
    public AppDetail detail(@PathVariable String appId) {
        var a = apps.get(appId);
        return AppDetail.from(a);
    }

    /** Làm mới trạng thái consent từ ledger và trả bản đầy đủ */
    @PostMapping("/{appId}/refresh-consent")
    public AppDetail refreshConsent(@PathVariable String appId) {
        var a = apps.refreshConsent(appId);
        return AppDetail.from(a);
    }

    /** Gọi chấm điểm theo hồ sơ (client gửi sẵn features) */
    @PostMapping(value = "/{appId}/score", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ScoreResponse score(@PathVariable String appId, @RequestBody Map<String, Object> body) {
        Object feats = body.get("features");
        if (!(feats instanceof Map)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing features");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> features = (Map<String, Object>) feats;
        try {
            return apps.score(appId, features);
        } catch (IllegalStateException ise) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ise.getMessage(), ise);
        }
    }

    /** Chấm điểm trực tiếp từ raw signals (server tự build features theo scope) */
    @PostMapping(value = "/{appId}/score-from-raw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ScoreResponse scoreFromRaw(@PathVariable String appId, @RequestBody RawSignalsRequest raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing raw payload");
        }
        try {
            return apps.scoreFromRaw(appId, raw);
        } catch (IllegalArgumentException iae) {
            // dữ liệu gửi lên có loại vượt ngoài scope, hoặc quá lớn
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, iae.getMessage(), iae);
        } catch (IllegalStateException ise) {
            // consent invalid / expired / revoked
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ise.getMessage(), ise);
        }
    }

    @GetMapping("/{appId}/reasons")
    public ReasonDtos.ReasonsResponse reasons(@PathVariable String appId,
                                              @RequestParam(defaultValue = "3") int topK,
                                              @RequestParam(defaultValue = "vi") String locale) {
        var app = apps.get(appId);
        var reasons = explain.topReasons(app, topK, locale);

        ReasonDtos.ReasonsResponse res = new ReasonDtos.ReasonsResponse();
        res.appId = app.appId;
        res.decision = app.decision;
        res.score = app.score;
        res.pd = app.pd;
        res.threshold = app.threshold;
        res.reasons = reasons;
        return res;
    }

    @PostMapping("/{appId}/revoke")
    public ConsentDtos.RevokeResponse revoke(@PathVariable String appId) {
        return apps.revoke(appId);
    }

    @GetMapping("/{appId}/proof")
    public ConsentDtos.ProofResponse proof(@PathVariable String appId) {
        return apps.proof(appId);
    }

    @GetMapping
    public com.demo.credit.controller.dto.ApplicationDtos.AppListResponse list(
            @RequestParam(required = false) String decision,
            @RequestParam(defaultValue = "all") String consent,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        var paged = apps.search(decision, consent, query, page, size, sort);

        var items = paged.items.stream().map(a -> {
            var s = new com.demo.credit.controller.dto.ApplicationDtos.AppSummary();
            s.appId = a.appId;
            s.userId = a.userId;
            s.consentActive = a.consentActive;
            s.decision = a.decision;
            s.score = a.score;
            s.updatedAt = a.updatedAt;
            return s;
        }).collect(java.util.stream.Collectors.toList());

        var res = new com.demo.credit.controller.dto.ApplicationDtos.AppListResponse();
        res.page = paged.page;
        res.size = paged.size;
        res.total = paged.total;
        res.totalPages = paged.totalPages;
        res.items = items;
        return res;
    }
}
