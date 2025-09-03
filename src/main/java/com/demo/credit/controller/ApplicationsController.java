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
        String consentId  = body.get("consentId");
        UUID appId = applicationService.create(applicantId, consentId);

        // Trả gọn: id + consentId (reference/status có thể lấy qua detail/list)
        return Map.of(
                "id", appId,
                "consentId", consentId
        );
    }

    // Danh sách hồ sơ (paging/filter) — trả thẳng record từ repository
    @GetMapping
    public List<ApplicationRepository.ApplicationListItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return applicationService.list(status, q, page, size);
    }

    // Chi tiết hồ sơ
    @GetMapping("/{id}")
    public ApplicationRepository.ApplicationDetail detail(@PathVariable("id") UUID id) {
        return applicationService.detail(id);
    }
}
