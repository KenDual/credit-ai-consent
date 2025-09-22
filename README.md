````md
# Credit-AI-Consent (MVP)

Hệ thống chấm điểm tín dụng thay thế (alternative credit) **consent-gated**: chỉ chấm điểm khi người vay đã **cấp quyền** hợp lệ. Dữ liệu phi truyền thống (SMS, danh bạ, mạng xã hội, e-commerce, web, email) được tổng hợp thành feature; mô hình ML trả về **Score (300–900)**, **PD**, **Decision** kèm **top-3 lý do (SHAP)**. Một **blockchain-lite consent ledger** giúp ghi vết **ConsentId/TxHash** để truy nguyên minh bạch.

---

## Kiến trúc ngắn gọn

- **Consent Ledger (Node.js)**: Sổ cái nhẹ lưu các block GIVE/REVOKE, API `/health`, `/wallets/new`, `/consents/give`, …
- **Model API (FastAPI, Python)**: `/health`, `/score` nhận `{features:{...}}` → trả `pd`, `score`, `decision`, `shapTopK`, `model_version`, `feature_schema_version`.
- **App Backend (Spring Boot, Java)**: API quản lý hồ sơ & chấm điểm:
  - `POST /applications`, `GET /applications`, `GET /applications/{id}`
  - `POST /score/{appId}` (verify consent → gọi Model API → lưu `Scores`)
  - Kèm **mini-UI** (Borrower & Risk console) ở `/` / `/risk`.

> Mặc định: Spring `:8080`, Model API `:8001`, Ledger `:3030`.

---

## Yêu cầu môi trường

- Node.js 18+
- Python 3.10+
- Java 17 (LTS) + Maven/Gradle
- SQL Server (LocalDB/Express/2019+)

---

## Thiết lập cơ sở dữ liệu

1. Mở SQL Server và chạy script trong thư mục `database.sql` (các bảng tối thiểu: `Applicants`, `Applications`, `Consents`, `RawSources`, `FeatureStore`, `Scores`; kèm stored procedures như `sp_CreateApplicant`, `sp_UpsertConsent`, `sp_CreateApplication`, `sp_SaveScore`, …).
2. Kiểm tra kết nối DB trong `src/main/resources/application.properties` (URL, user, password).

> DB mặc định: `jdbc:sqlserver://localhost:1433;databaseName=CreditAIConsent` (có `encrypt=true;trustServerCertificate=true`).

---

## Cấu hình ứng dụng

Trong `application.properties`:

```properties
# Endpoints nội bộ
model.baseUrl=http://127.0.0.1:8001
ledger.baseUrl=http://127.0.0.1:3030

# Spring
server.port=8080

# Cấu hình datasource (đổi user/password theo máy)
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=CreditAIConsent;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=yourStrong(!)Password
````

---

## Cách chạy

### 1. Chạy **Consent-Ledger** (Blockchain-lite)

```bash
cd consent-ledger
npm install
# (Tùy chọn) bật chế độ demo không nghiêm ngặt chữ ký
INSECURE_LEDGER=1 node src/server.js
# Server sẽ lắng nghe tại http://127.0.0.1:3030
```

> Kiểm tra nhanh:

```bash
curl http://127.0.0.1:3030/health
```

### 2. Chạy **Model API** (FastAPI)

```bash
cd ai
python -m venv .venv && source .venv/bin/activate  # hoặc .venv\Scripts\activate trên Windows
pip install -r requirements.txt
```

Chạy service (ví dụ dùng `uvicorn`):

```bash
uvicorn service:app --host 127.0.0.1 --port 8001  # Điều chỉnh module:app theo cấu trúc mã của bạn
```

> Kiểm tra nhanh:

```bash
curl http://127.0.0.1:8001/health
curl -X POST http://127.0.0.1:8001/score \
  -H "Content-Type: application/json" \
  -d '{"features":{"sms_30d":120,"sms_fin_ratio":0.2,"contacts_total":180,"email_txn_30d":25,"email_overdue_ratio":0.05,"social_posts_30d":30,"social_eng_approx":220,"ecom_orders_90d":8,"ecom_aov":420000,"ecom_late_ratio":0.0,"web_fintech_30d":18,"web_career_30d":10,"web_gambling_30d":0}}'
```

### 3. Chạy **App Backend** (Spring Boot)

**Maven**:

```bash
./mvnw spring-boot:run
# hoặc: mvn spring-boot:run
```

**Gradle**:

```bash
./gradlew bootRun
```

> Mở trình duyệt:

* Borrower UI: `http://127.0.0.1:8080/`
* Risk console (danh sách): `http://127.0.0.1:8080/risk`
* Risk detail: `http://127.0.0.1:8080/risk/details`

---

## API tóm tắt

### **Consent-Ledger (Node.js)**

* `GET /health` → Kiểm tra trạng thái sổ cái.
* `POST /wallets/new` → Tạo ví demo.
* `POST /consents/give` → Ghi block GIVE, trả `consentId`, `txHash`.
* `POST /consents/revoke` → Ghi block REVOKE (nếu có).

### **Model API (FastAPI)**

* `GET /health` → Kiểm tra trạng thái API.
* `POST /score`

  * **Body**: `{ "features": { ... } }`
  * **Resp.**: `{ "pd", "score", "decision", "shapTopK": [...], "model_version", "feature_schema_version" }`

### **App Backend (Spring Boot)**

* `POST /applications` → Tạo hồ sơ, liên kết consent.
* `GET /applications?status=&q=&page=&size=` → Danh sách hồ sơ (phân trang, sort `createdAt`).
* `GET /applications/{id}` → Chi tiết hồ sơ + consent proof + điểm số mới nhất.
* `POST /score/{appId}` → Verify consent → Gọi Model API → Lưu điểm.

---

## Cấu trúc thư mục gợi ý

```
credit-ai-consent/
├─ consent-ledger/              # Node.js ledger (server.js, routes, scripts)
├─ ai/                          # FastAPI model service (service.py, requirements.txt)
├─ app/                         # Spring Boot (Java)
│  ├─ src/main/java/.../controller/
│  ├─ src/main/java/.../service/
│  ├─ src/main/java/.../repository/
│  ├─ src/main/resources/
│  │  ├─ application.properties
│  │  ├─ templates/             # borrower.html, risk-list.html, risk-detail.html
│  │  └─ static/                # css/, js/
├─ database/                    # schema.sql, seed.sql (nếu có)
└─ scripts/                     # run_chain.sh, run_ai.sh, run_app.sh (tuỳ chọn)
```

---

## Ngưỡng quyết định & minh bạch

* **Decision (mặc định)**:

  * `APPROVE` nếu **score ≥ 700**
  * `REVIEW` nếu **650 ≤ score < 700**
  * `REJECT` nếu **score < 650**
* **Minh bạch**:

  * Lưu kèm `ConsentId/TxHash` trong `Scores`.
  * Trả về **top-3 lý do** (SHAP), `model_version`, `feature_schema_version`.


---

## Roadmap ngắn (MVP)

* [x] Consent-gated scoring end-to-end
* [x] Tối thiểu 6 nguồn dữ liệu (SMS, contacts, social, e-commerce, web, email)
* [x] Feature builder + schema + unit test cơ bản
* [x] SHAP top-3 lý do
* [x] UI tối giản (Borrower / Risk)
* [x] Truy vết `ConsentId/TxHash` trong quyết định
* [ ] Script “một chạm”: `run_chain`, `run_ai`, `run_app`, `seed`
* [ ] Kiểm thử chấp nhận: APPROVE / REVIEW / REJECT; **revoke consent** → từ chối chấm điểm
* [ ] Tách PII (vault) khỏi FeatureStore, mask log, HTTPS khi triển khai thật

---

## License

MIT (demo/research). Không dùng trực tiếp cho production nếu chưa đánh giá bảo mật, quyền riêng tư và tuân thủ pháp lý.

