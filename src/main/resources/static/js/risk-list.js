const $ = s => document.querySelector(s);

const fmt = s => {
    if (!s) return '';
    const t = String(s).replace('Z', ''); // đề phòng nếu có 'Z'
    return t.replace('T', ' ').slice(0, 16); // YYYY-MM-DD HH:mm
};

const normalize = resp => (Array.isArray(resp) ? resp : (resp?.items || resp?.content || []));

$("#btnLoad").onclick = async () => {
    const params = new URLSearchParams();
    const status = $("#status").value;
    const q = $("#q").value;
    if (status) params.set("status", status);
    if (q) params.set("q", q);

    const resp = await api.get(`/applications?${params}`);
    const items = normalize(resp);

    $("#rows").innerHTML = items
        .map(x => {
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
        })
        .join("");
};

$("#btnLoad").click();
