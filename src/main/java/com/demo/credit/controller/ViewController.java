package com.demo.credit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {
    
    @GetMapping("/")
    public String home() {
        return "borrower";
    }

    @GetMapping("/borrower")
    public String borrower() {
        return "borrower";
    }

    @GetMapping("/risk")
    public String riskList() {
        return "risk-list";
    }

    @GetMapping("/risk/details")
    public String riskDetail() {
        return "risk-detail";
    }
}
