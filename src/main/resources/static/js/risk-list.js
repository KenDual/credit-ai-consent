const $ = s => document.querySelector(s);
const fmt = s => s ? new Date(s).toISOString().slice(0, 16).replace('T', ' ') : '';
$("#btnLoad").onclick = async () => {
    const params = new URLSearchParams();
    const status = $("#status").value; const q = $("#q").value;
    if (status) params.set("status", status);
    if (q) params.set("q", q);
    const items = await api.get(`/applications?${params}`);
    $("#rows").innerHTML = (items || []).map(x => `
    <tr class="border-t">
      <td>${x.referenceNo || ''}</td>
      <td>${fmt(x.createdAt)}</td>
      <td>${x.status || ''}</td>
      <td>${x.score ?? ''}</td>
      <td>${x.pd ?? ''}</td>
      <td>${x.decision || ''}</td>
      <td><a class="text-blue-600" href="/risk/detail?id=${x.applicationId}">Xem</a></td>
    </tr>`).join('');
};
$("#btnLoad").click();
