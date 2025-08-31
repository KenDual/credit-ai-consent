package com.demo.credit.service;

import com.demo.credit.controller.dto.ReasonDtos;
import com.demo.credit.repository.LoanApplication;
import com.demo.credit.service.dto.ScoreResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ExplainService {

    public List<ReasonDtos.Reason> topReasons(LoanApplication app, int topK, String locale) {
        List<ReasonDtos.Reason> out = new ArrayList<>();
        if (app == null || app.shapTopK == null || app.shapTopK.isEmpty()) return out;

        Locale lc = (locale != null && locale.toLowerCase().startsWith("vi")) ? Locale.forLanguageTag("vi") : Locale.ENGLISH;

        int k = Math.max(1, topK);
        int n = Math.min(k, app.shapTopK.size());
        for (int i = 0; i < n; i++) {
            ScoreResponse.ShapItem it = app.shapTopK.get(i);
            ReasonDtos.Reason r = new ReasonDtos.Reason();
            r.feature   = it.getFeature();
            r.value     = it.getValue();
            r.shap      = it.getShap();
            r.absShap   = it.getAbsShap();
            r.direction = it.getDirection();

            String[] tt = mapToTitleAndText(lc, it.getFeature(), it.getValue(), it.getDirection(), it.getAbsShap());
            r.title = tt[0];
            r.text  = tt[1];

            out.add(r);
        }
        return out;
    }

    // Mapping đơn giản cho MVP; có thể mở rộng sau này
    private String[] mapToTitleAndText(Locale lc, String feature, Object value, String dir, double abs) {
        boolean vi = lc.getLanguage().equals("vi");
        String direction = "pos".equalsIgnoreCase(dir) ? (vi ? "tăng rủi ro" : "increases risk")
                : (vi ? "giảm rủi ro" : "decreases risk");
        String v = (value == null) ? "N/A" : String.valueOf(value);
        String contrib = String.format("%.3f", abs);

        String titleVi, textVi, titleEn, textEn;

        switch (feature) {
            case "email_overdue_ratio":
                titleVi = "Tỷ lệ email quá hạn";
                textVi  = "Tỷ lệ email nhắc nợ/quá hạn = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Email overdue ratio";
                textEn  = "Overdue/collection email ratio = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "sms_fin_ratio":
                titleVi = "Mật độ SMS tài chính";
                textVi  = "Tỷ lệ SMS liên quan tài chính = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Financial SMS density";
                textEn  = "Share of finance-related SMS = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "sms_count":
                titleVi = "Số lượng SMS";
                textVi  = "Tổng số SMS = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Total SMS count";
                textEn  = "Total SMS = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "contacts_count":
                titleVi = "Quy mô danh bạ";
                textVi  = "Tổng số liên hệ = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Contacts size";
                textEn  = "Contacts count = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "ecom_cat_fashion_ratio":
                titleVi = "Tỷ lệ chi tiêu thời trang";
                textVi  = "Tỷ trọng giao dịch 'fashion' = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Fashion spending ratio";
                textEn  = "Share of 'fashion' in e-commerce = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "social_posts_sum":
                titleVi = "Hoạt động mạng xã hội (bài viết)";
                textVi  = "Tổng số bài đăng = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Social activity (posts)";
                textEn  = "Total posts = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "social_likes_sum":
                titleVi = "Tương tác mạng xã hội (lượt thích)";
                textVi  = "Tổng số lượt thích = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Social engagement (likes)";
                textEn  = "Total likes = " + v + " " + direction + " (" + contrib + ").";
                break;
            case "monthly_income_vnd":
                titleVi = "Thu nhập hàng tháng";
                textVi  = "Thu nhập (VND) = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Monthly income";
                textEn  = "Income (VND) = " + v + " " + direction + " (" + contrib + ").";
                break;
            default:
                titleVi = "Yếu tố: " + feature;
                textVi  = "Giá trị = " + v + " " + direction + " (" + contrib + ").";
                titleEn = "Feature: " + feature;
                textEn  = "Value = " + v + " " + direction + " (" + contrib + ").";
        }

        return (vi ? new String[]{titleVi, textVi} : new String[]{titleEn, textEn});
    }
}
