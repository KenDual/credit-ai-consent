import express from "express";
import { Ledger } from "./ledger.js";
import { newPrivateKeyHex, getPublicKeyHex, messageHashHex } from "./crypto.js";

const app = express();
app.use(express.json({ limit: "1mb" }));

// Health
app.get("/health", (_req, res) => res.json({ status: "ok" }));

// Tạo ví demo (đừng dùng cho production)
app.post("/wallets/new", (_req, res) => {
    const priv = newPrivateKeyHex();
    const pub = getPublicKeyHex(priv);
    res.json({ privateKey: priv, publicKey: pub });
});

// Ghi consent (GIVE)
app.post("/consents/give", async (req, res) => {
    try {
        const { scopes, expiry, dataHash, subjectPubKey, signature } = req.body || {};
        if (!scopes || !expiry || !dataHash || !subjectPubKey || !signature)
            return res.status(400).json({ error: "Missing fields" });

        const ledger = await Ledger.load();
        const out = await ledger.addGive({ scopes, expiry, dataHash, subjectPubKey, signature });
        const chainCheck = ledger.verifyChain();
        res.json({ ok: true, ...out, chainCheck });
    } catch (e) {
        res.status(400).json({ ok: false, error: String(e.message || e) });
    }
});

// Thu hồi consent (REVOKE)
app.post("/consents/revoke", async (req, res) => {
    try {
        const { consentId, subjectPubKey, signature } = req.body || {};
        if (!consentId || !subjectPubKey || !signature)
            return res.status(400).json({ error: "Missing fields" });

        const ledger = await Ledger.load();
        const out = await ledger.addRevoke({ consentId, subjectPubKey, signature });
        const chainCheck = ledger.verifyChain();
        res.json({ ok: true, ...out, chainCheck });
    } catch (e) {
        res.status(400).json({ ok: false, error: String(e.message || e) });
    }
});

// Tra cứu trạng thái consent
app.get("/consents/:id/status", async (req, res) => {
    const ledger = await Ledger.load();
    const st = ledger.statusOf(req.params.id);
    res.json(st);
});

// Xem chain & kiểm tra
app.get("/chain", async (_req, res) => {
    const ledger = await Ledger.load();
    res.json({ length: ledger.blocks.length, tip: ledger.tip(), blocks: ledger.blocks });
});
app.get("/chain/verify", async (_req, res) => {
    const ledger = await Ledger.load();
    res.json(ledger.verifyChain());
});

const PORT = process.env.PORT || 3030;
app.listen(PORT, () => console.log(`Consent-Ledger listening on http://127.0.0.1:${PORT}`));
