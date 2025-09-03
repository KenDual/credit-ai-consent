const api = {
    async get(path) {
        const r = await fetch(path, { headers: { "Accept": "application/json" } });
        if (!r.ok) throw new Error(await r.text());
        return r.status === 204 ? null : r.json();
    },
    async post(path, body) {
        const r = await fetch(path, {
            method: "POST",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(body || {})
        });
        if (!r.ok) throw new Error(await r.text());
        return r.json();
    }
};
