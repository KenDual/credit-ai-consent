package com.demo.credit.controller;

import com.demo.credit.repository.ApplicationRepository;
import com.demo.credit.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationsController {

    private final ApplicationService applicationService;

    // Tạo hồ sơ — body rất gọn, không DTO
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        UUID applicantId = UUID.fromString(body.get("applicantId"));
        String consentId = body.get("consentId");
        UUID appId = applicationService.create(applicantId, consentId);

        // Trả gọn: id + consentId (reference/status có thể lấy qua detail/list)
        return Map.of(
                "id", appId,
                "consentId", consentId);
    }

    // Danh sách hồ sơ (paging/filter) — trả về mảng Map có khóa 'id' nhất quán
    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var items = applicationService.list(status, q, page, size);
        return items.stream()
                .map(x -> Map.<String, Object>ofEntries(
                        Map.entry("id", x.applicationId()), // luôn có 'id'
                        Map.entry("applicationId", x.applicationId()), // giữ thêm khóa cũ cho FE
                        Map.entry("referenceNo", x.referenceNo()),
                        Map.entry("status", x.status()),
                        Map.entry("createdAt", x.createdAt()),
                        Map.entry("applicantId", x.applicantId()),
                        Map.entry("consentId", x.consentId()),
                        Map.entry("score", x.score()),
                        Map.entry("pd", x.pd()),
                        Map.entry("decision", x.decision()),
                        Map.entry("scoredAt", x.scoredAt())))
                .toList();
    }

    // Chi tiết hồ sơ
    @GetMapping("/{id}")
    public ApplicationRepository.ApplicationDetail detail(@PathVariable("id") UUID id) {
        return applicationService.detail(id);
    }
}
