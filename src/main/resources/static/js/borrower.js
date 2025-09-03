const $ = s => document.querySelector(s);
const toDoubleMap = obj => Object.fromEntries(Object.entries(obj || {}).filter(([, v]) => !isNaN(+v)).map(([k, v]) => [k, Number(v)]));

$("#btnConsent").onclick = async () => {
    const body = {
        consentId: $("#consentId").value.trim(),
        applicantId: $("#applicantId").value.trim(),
        expiry: $("#expiry").value.trim(),
        lastTxHash: $("#lastTxHash").value.trim(),
        scopesJson: $("#scopesJson").value.trim() || "{}",
        status: "ACTIVE",
        subjectPubKey: ""
    };
    const res = await api.post("/consents", body);
    $("#consentResp").textContent = JSON.stringify(res, null, 2);
};

$("#btnCheck").onclick = async () => {
    const id = $("#consentId").value.trim();
    const res = await api.get(`/consents/${id}`);
    $("#consentResp").textContent = JSON.stringify(res, null, 2);
};

$("#btnCreateApp").onclick = async () => {
    const res = await api.post("/applications", {
        applicantId: $("#applicantId").value.trim(),
        consentId: $("#consentId").value.trim()
    });
    $("#appId").textContent = res.id || "";
};

$("#btnScore").onclick = async () => {
    const appId = $("#appId").textContent.trim();
    const body = {
        consentId: $("#consentId").value.trim(),
        txHash: $("#lastTxHash").value.trim(),
        features: toDoubleMap(JSON.parse($("#features").value || "{}"))
    };
    const res = await api.post(`/score/${appId}`, body);
    $("#scoreResp").textContent = JSON.stringify(res, null, 2);
};
