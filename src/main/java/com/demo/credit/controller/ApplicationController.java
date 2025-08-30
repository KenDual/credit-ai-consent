package com.demo.credit.controller;

import com.demo.credit.controller.dto.ApplicationDtos.*;
import com.demo.credit.controller.dto.ConsentDtos;
import com.demo.credit.controller.dto.RawSignalsRequest;
import com.demo.credit.service.ApplicationService;
import com.demo.credit.service.dto.ScoreResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

    private final ApplicationService apps;

    public ApplicationController(ApplicationService apps) {
        this.apps = apps;
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

    /** Thu hồi consent của hồ sơ */
    @PostMapping("/{appId}/revoke")
    public ConsentDtos.RevokeResponse revoke(@PathVariable String appId) {
        return apps.revoke(appId);
    }

    /** Proof consent từ ledger cho hồ sơ */
    @GetMapping("/{appId}/proof")
    public ConsentDtos.ProofResponse proof(@PathVariable String appId) {
        return apps.proof(appId);
    }
}
