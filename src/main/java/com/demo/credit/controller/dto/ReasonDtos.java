package com.demo.credit.controller.dto;

import java.util.List;

public final class ReasonDtos {
    private ReasonDtos() {}

    public static class Reason {
        public String feature;
        public Object value;     // để hiển thị giá trị gốc (có thể int/double)
        public double shap;      // đóng góp (+/-)
        public double absShap;   // độ mạnh
        public String direction; // "pos"/"neg"
        public String title;     // tiêu đề ngắn gọn
        public String text;      // diễn giải chi tiết
    }

    public static class ReasonsResponse {
        public String appId;
        public String decision;
        public Integer score;
        public Double pd;
        public Double threshold;
        public List<Reason> reasons;
    }
}
