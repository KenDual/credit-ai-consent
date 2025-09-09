const $ = s => document.querySelector(s);

const fmt = s => {
    if (!s) return '';
    const t = String(s).replace('Z', '');
    return t.replace('T', ' ').slice(0, 16);
};

const normalize = resp => (Array.isArray(resp) ? resp : (resp?.items || resp?.content || []));

const showDbError = (message) => {
    const el = $("#dbError");
    if (el) {
        el.style.display = "block";
        el.textContent = message || "Không thể kết nối cơ sở dữ liệu. Vui lòng kiểm tra cấu hình hoặc trạng thái SQL Server.";
    } else {
        alert(message || "Không thể kết nối cơ sở dữ liệu. Vui lòng kiểm tra cấu hình hoặc trạng thái SQL Server.");
    }
};

const hideDbError = () => {
    const el = $("#dbError");
    if (el) el.style.display = "none";
};

const renderRows = (items) => {
    if (!items || items.length === 0) {
        $("#rows").innerHTML = `<tr><td colspan="7" class="text-center py-4 text-gray-500">Không có dữ liệu</td></tr>`;
        return;
    }
    $("#rows").innerHTML = items.map(x => {
        const id = x.id || x.applicationId;
        return `
      <tr class="border-t">
        <td>${x.referenceNo || ""}</td>
        <td>${fmt(x.createdAt)}</td>
        <td>${x.status || ""}</td>
        <td>${x.score ?? ""}</td>
        <td>${x.pd ?? ""}</td>
        <td>${x.decision || ""}</td>
        <td><a class="text-blue-600" href="/risk/detail?id=${id}">Xem</a></td>
      </tr>`;
    }).join("");
};

const loadApplications = async () => {
    const btn = $("#btnLoad");
    try {
        hideDbError();
        if (btn) { btn.disabled = true; btn.dataset._txt = btn.textContent; btn.textContent = "Đang tải..."; }

        const params = new URLSearchParams();
        const status = $("#status")?.value;
        const q = $("#q")?.value;
        if (status) params.set("status", status);
        if (q) params.set("q", q);

        const resp = await api.get(`/applications?${params}`);

        if (resp && (resp.error === "DB_DOWN")) {
            showDbError(resp.message || "Không thể kết nối cơ sở dữ liệu.");
            renderRows([]); // clear bảng
            return;
        }

        const items = normalize(resp);
        renderRows(items);
    } catch (err) {
        console.error("Load applications error:", err);

        // Nhận diện các kiểu error thường gặp từ api.js / fetch
        const status = err?.status || err?.response?.status;
        if (status === 503) {
            showDbError("Không thể kết nối cơ sở dữ liệu (503). Vui lòng thử lại sau.");
            renderRows([]);
            return;
        }

        // Nếu server trả JSON lỗi
        const data = err?.response?.data || err?.data;
        if (data && (data.error === "DB_DOWN")) {
            showDbError(data.message || "Không thể kết nối cơ sở dữ liệu.");
            renderRows([]);
            return;
        }

        // Fallback lỗi khác
        showDbError(err?.message || "Đã xảy ra lỗi không xác định.");
        renderRows([]);
    } finally {
        if (btn) { btn.disabled = false; if (btn.dataset._txt) btn.textContent = btn.dataset._txt; }
    }
};

// Gán sự kiện
$("#btnLoad")?.addEventListener("click", loadApplications);

// Tải lần đầu
$("#btnLoad")?.click();
