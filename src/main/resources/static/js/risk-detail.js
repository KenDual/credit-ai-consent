const $ = s => document.querySelector(s);
(async () => {
    const id = new URLSearchParams(location.search).get("id");
    const data = await api.get(`/applications/${id}`);
    $("#detail").textContent = JSON.stringify(data, null, 2);
})();
