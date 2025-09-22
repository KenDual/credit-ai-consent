# Credit-AI-Consent (MVP)

Hệ thống chấm điểm tín dụng thay thế (alternative credit) **consent-gated**: chỉ chấm điểm khi người vay đã **cấp quyền** hợp lệ. Dữ liệu phi truyền thống (SMS, danh bạ, mạng xã hội, e-commerce, web, email) được tổng hợp thành feature; mô hình ML trả về **Score (300–900)**, **PD**, **Decision** kèm **top-3 lý do (SHAP)**. Một **blockchain-lite consent ledger** giúp ghi vết ConsentId/TxHash để truy nguyên minh bạch.

## Kiến trúc ngắn gọn

- **Consent Ledger (Node.js)**: sổ cái nhẹ lưu các block GIVE/REVOKE, API `/health`, `/wallets/new`, `/consents/give`, …  
- **Model API (FastAPI, Python)**: `/health`, `/score` nhận `{features:{...}}` → trả `pd, score, decision, shapTopK, model_version, feature_schema_version`.  
- **App Backend (Spring Boot, Java)**: API quản lý hồ sơ & chấm điểm:  
  - `POST /applications`, `GET /applications`, `GET /applications/{id}`  
  - `POST /score/{appId}` (verify consent → gọi Model API → lưu `Scores`)  
  - kèm **mini-UI** (Borrower & Risk console) ở `/` / `/risk`.

> Mặc định: Spring `:8080`, Model API `:8001`, Ledger `:3030`.

---

## Yêu cầu môi trường

- Node.js 18+  
- Python 3.10+  
- Java 17 (LTS) + Maven/Gradle  
- SQL Server (LocalDB/Express/2019+)

---

## Thiết lập cơ sở dữ liệu

1. Mở SQL Server, chạy script trong thư mục `database.sql` (bảng tối thiểu: `Applicants`, `Applications`, `Consents`, `RawSources`, `FeatureStore`, `Scores`; kèm SP như `sp_CreateApplicant`, `sp_UpsertConsent`, `sp_CreateApplication`, `sp_SaveScore`, …).  
2. Kiểm tra kết nối DB trong `src/main/resources/application.properties` (URL, user, password).

> DB mặc định: `jdbc:sqlserver://localhost:1433;databaseName=CreditAIConsent` (có `encrypt=true;trustServerCertificate=true`).

---

## Cấu hình ứng dụng

Trong `application.properties`:

```properties
# endpoints nội bộ
model.baseUrl=http://127.0.0.1:8001
ledger.baseUrl=http://127.0.0.1:3030

# spring
server.port=8080

# datasource (đổi user/password theo máy)
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=CreditAIConsent;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=yourStrong(!)Password 

## Cách chạy
** Chạy Consent-ledger (Blockchain-lite)
cd consent-ledger
npm install
# (tuỳ chọn) bật chế độ demo không nghiêm ngặt chữ ký
INSECURE_LEDGER=1 node src/server.js
# server lắng nghe http://127.0.0.1:3030

## Chạy Model API (FastAPI)
cd ai
python -m venv .venv && source .venv/bin/activate  # hoặc .venv\Scripts\activate trên Windows
pip install -r requirements.txt

## chạy service (ví dụ dùng uvicorn)
uvicorn service:app --host 127.0.0.1 --port 8001  # điều chỉnh module:app theo cấu trúc mã của bạn



