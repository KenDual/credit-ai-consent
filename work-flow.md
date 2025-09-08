flowchart TD
  A[1) Input features\n(JSON 6 nguồn + nhân khẩu học)] --> B[2) Kiểm tra & Chuẩn hóa\n- bắt buộc trường\n- domain check (0..1)\n- điền default/NA\n- clip extreme]
  B --> C[3) Vector hóa cuối cùng\n- đúng order/schema_version]
  C --> D[4) LightGBM Inference\n- duyệt từng cây -> rơi lá -> cộng điểm lá\n- ra log-odds f(x)]
  D --> E[5) Sigmoid\np_raw = 1/(1+e^-f(x))]
  E --> F[6) Hiệu chỉnh xác suất (Isotonic)\np_cal = iso(p_raw)]
  F --> G{7) Prior shift (tùy chọn)\nCó runtime_prior?}
  G -- Có --> H[pd = Saerens(p_cal, prior_runtime)]
  G -- Không --> I[pd = p_cal]
  H --> J
  I --> J[8) Ánh xạ PD -> Score\nscore = 600 + 50*log2(((1-pd)/pd)/20)\nclip 300..900]
  J --> K{9) Ngưỡng quyết định}
  K -- score ≥ 700 --> L[APPROVE]
  K -- 650 ≤ score < 700 --> M[REVIEW]
  K -- score < 650 --> N[REJECT]
  C --> S[10) SHAP\n- tính đóng góp từng feature]
  S --> O[11) Giải thích\nTop-3 lý do ±]
  L --> P[12) Output JSON\nscore, pd, decision, reasons, versions]
  M --> P
  N --> P
