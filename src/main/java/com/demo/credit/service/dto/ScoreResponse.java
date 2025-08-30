package com.demo.credit.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreResponse {
    public double pd;
    public int score;
    public String decision;
    public double threshold;

    // JSON là snake_case -> dùng @JsonProperty để map chính xác
    @JsonProperty("missing_features")
    public List<String> missingFeatures;

    public List<ShapItem> shapTopK;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShapItem {
        public String feature;
        public double value;
        public double shap;
        @JsonProperty("abs_shap")
        public double absShap;
        public String direction;
    }
}
