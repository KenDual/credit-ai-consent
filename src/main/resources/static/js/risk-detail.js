// risk-detail.js
const $ = s => document.querySelector(s);
(async () => {
    const id = new URLSearchParams(location.search).get("id");
    if (!id) {
        $("#detail").textContent = "Thiếu tham số id";
        return;
    }
    const data = await api.get(`/applications/${id}`);
    $("#detail").textContent = JSON.stringify(data, null, 2);
})();
