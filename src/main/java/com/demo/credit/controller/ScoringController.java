package com.demo.credit.controller;

import com.demo.credit.service.ConsentService;
import com.demo.credit.service.ModelClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import java.util.Map;

@Controller
@RequestMapping("/score")
public class ScoringController {
    private final ConsentService consent;
    private final ModelClient model;

    public ScoringController(ConsentService consent, ModelClient model) {
        this.consent = consent;
        this.model = model;
    }

    @PostMapping("/{appId}")
    public String score(@PathVariable int appId,
                        @RequestParam String consentId,
                        @RequestBody Map<String,Object> rawPayload,
                        Model m) throws Exception {
        if (!consent.isActive(consentId)) {
            m.addAttribute("error", "Consent chưa hợp lệ/đã thu hồi");
            return "error";
        }
        var req = Map.of(
            "app_id", appId,
            "raw", rawPayload.get("raw"),
            "loan", rawPayload.getOrDefault("loan", Map.of("amount", 10000000, "tenor", 12))
        );
        var result = model.score(req);
        m.addAttribute("result", result);
        return "result"; // thymeleaf template
    }
}
