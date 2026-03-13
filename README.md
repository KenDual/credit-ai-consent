

-----

# Credit-AI-Consent (MVP)


**Credit-AI-Consent** là hệ thống chấm điểm tín dụng thay thế dựa trên nguyên tắc **Consent-gated**, chỉ thực hiện khi người vay đã **cấp quyền** hợp lệ.

Dự án tích hợp dữ liệu phi truyền thống (SMS, danh bạ, mạng xã hội, e-commerce, web, email) để tổng hợp thành các đặc điểm (features), sau đó mô hình học máy sẽ trả về **Score (300–900)**, **Probability of Default (PD)**, **Decision** và **Top-3 lý do** (SHAP). Hệ thống sử dụng **blockchain-lite consent ledger** để ghi vết **ConsentId/TxHash**, nhằm đảm bảo tính minh bạch và khả năng truy nguyên dữ liệu.

### Tính năng chính

  - **Consent-gated scoring**: Chấm điểm tín dụng chỉ khi người vay cấp quyền hợp lệ.
  - **Sử dụng dữ liệu phi truyền thống**: SMS, danh bạ, mạng xã hội, e-commerce, email, web.
  - **Hệ thống học máy**: Trả về **score** (300–900), **PD**, **decision**, và **SHAP** cho top-3 lý do.
  - **Blockchain-lite Ledger**: Đảm bảo tính minh bạch với **ConsentId** và **TxHash**.
  - **Microservices**: Xây dựng bằng Node.js, FastAPI (Python), và Spring Boot (Java).


-----

## Cài đặt

### Yêu cầu

Đảm bảo bạn đã cài đặt các công cụ sau trước khi bắt đầu:

  - **Node.js** 18+
  - **Python** 3.10+
  - **Java** 17 (LTS) + Maven/Gradle
  - **SQL Server** (LocalDB/Express/2019+)

### Hướng dẫn cài đặt và cấu hình

1.  **Clone Repository**

    ```bash
    git clone https://github.com/KenDual/credit-ai-consent.git
    cd credit-ai-consent
    ```

2.  **Thiết lập Consent-Ledger (Node.js)**

    ```bash
    cd consent-ledger
    npm install
    ```

3.  **Thiết lập Model API (FastAPI)**

    ```bash
    cd ai
    python -m venv .venv
    source ./.venv/bin/activate  # Trên Windows: .venv\Scripts\activate
    pip install -r requirements.txt
    ```

4.  **Thiết lập App Backend (Spring Boot)**

      - Sử dụng Maven hoặc Gradle để tải các thư viện cần thiết.

-----

## Cách sử dụng

Để chạy toàn bộ hệ thống, bạn cần khởi động từng thành phần microservice trong các terminal riêng biệt.

1.  **Khởi động Consent-Ledger (Node.js)**

      - Trong thư mục `consent-ledger`, chạy lệnh sau:

    <!-- end list -->

    ```bash
    $env:INSECURE_LEDGER='1'; npm start
    ```

      - Server sẽ lắng nghe tại: `http://127.0.0.1:3030`

2.  **Khởi động Model API (FastAPI)**

      - Trong thư mục `ai`, kích hoạt môi trường ảo và chạy server:

    <!-- end list -->

    ```bash
    ./.venv/Scripts/Activate
    uvicorn service:app --host 127.0.0.1 --port 8001  # Điều chỉnh module:app theo cấu trúc mã của bạn
    ```

3.  **Khởi động App Backend (Spring Boot)**

      - Trong thư mục `app`, sử dụng Maven hoặc Gradle:

    **Với Maven:**

    ```bash
    ./mvnw spring-boot:run
    # hoặc: mvn spring-boot:run
    ```

    **Với Gradle:**

    ```bash
    ./gradlew bootRun
    ```

### Giao diện người dùng

Truy cập các URL sau trên trình duyệt của bạn:

  - **Borrower UI:** `http://127.0.0.1:8080/`
  - **Risk Console:** `http://127.0.0.1:8080/risk`
  - **Risk Detail:** `http://127.0.0.1:8080/risk/details`

-----

## Video Demo
https://www.youtube.com/watch?v=t3CfpNxC8C4

## Contact

  - **Tác giả:** KenDual
  - **Email:** `maiphuhai123@gmail.com`
