import express from "express";
import { Ledger } from "./ledger.js";
import { newPrivateKeyHex, getPublicKeyHex } from "./crypto.js";

const INSECURE = process.env.INSECURE_LEDGER === "1";
const app = express();
app.use(express.json({ limit: "1mb" }));

// Health: kèm trạng thái chuỗi & kết quả verify
app.get("/health", async (_req, res) => {
    const ledger = await Ledger.load();
    const check = ledger.verifyChain();
    res.json({
        status: "ok",
        chainLength: ledger.blocks.length,
        valid: check.valid,
        detail: check
    });
});

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
        if (!scopes || !expiry || !dataHash) {
            return res.status(400).json({ error: "Missing fields" });
        }
        if (!INSECURE && (!subjectPubKey || !signature)) {
            return res.status(400).json({ error: "Missing signature/pubKey" });
        }

        const ledger = await Ledger.load();
        const out = await ledger.addGive(
            {
                scopes,
                expiry,
                dataHash,
                subjectPubKey: subjectPubKey || "insecure",
                signature: signature || "insecure"
            },
            { skipVerify: INSECURE }
        );
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
        if (!consentId) {
            return res.status(400).json({ error: "Missing fields" });
        }
        if (!INSECURE && (!subjectPubKey || !signature)) {
            return res.status(400).json({ error: "Missing signature/pubKey" });
        }

        const ledger = await Ledger.load();

        // Ở chế độ insecure, nếu không gửi pubKey thì dùng pubKey từ block GIVE để đảm bảo tính nhất quán
        let pub = subjectPubKey;
        if (INSECURE && !pub) {
            const st = ledger.statusOf(consentId);
            if (!st.found) return res.status(404).json({ error: "consentId not found" });
            pub = st.subjectPubKey || "insecure";
        }

        const out = await ledger.addRevoke(
            {
                consentId,
                subjectPubKey: pub,
                signature: signature || "insecure"
            },
            { skipVerify: INSECURE }
        );
        const chainCheck = ledger.verifyChain();
        res.json({ ok: true, ...out, chainCheck });
    } catch (e) {
        res.status(400).json({ ok: false, error: String(e.message || e) });
    }
});

// Tra cứu trạng thái consent theo consentId
app.get("/consents/:id/status", async (req, res) => {
    const ledger = await Ledger.load();
    const st = ledger.statusOf(req.params.id);
    res.json(st);
});

// Bằng chứng chuỗi block cho consentId
app.get("/consents/:id/proof", async (req, res) => {
    const ledger = await Ledger.load();
    res.json(ledger.getProof(req.params.id));
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
