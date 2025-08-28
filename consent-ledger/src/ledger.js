import { messageHashHex, sha256Hex, verifySig, canonicalize } from "./crypto.js";
import { loadLedgerFile, saveLedgerFile, ensureStorage } from "./storage.js";

function computeBlockHash(block) {
    // Hash mọi trường trừ 'hash' (để bất biến)
    const toHash = {
        index: block.index,
        timestamp: block.timestamp,
        type: block.type,
        payload: block.payload,
        subjectPubKey: block.subjectPubKey,
        signature: block.signature,
        prevHash: block.prevHash
    };
    return sha256Hex(canonicalize(toHash));
}

export class Ledger {
    constructor(data) {
        this.blocks = data?.blocks || [];
    }

    static async load() {
        await ensureStorage();
        const data = await loadLedgerFile();
        return new Ledger(data);
    }

    async save() {
        await saveLedgerFile({ blocks: this.blocks });
    }

    tip() {
        return this.blocks[this.blocks.length - 1];
    }

    verifyChain() {
        for (let i = 1; i < this.blocks.length; i++) {
            const b = this.blocks[i];
            const prev = this.blocks[i - 1];
            if (b.prevHash !== prev.hash) {
                return { valid: false, index: i, reason: "prevHash mismatch" };
            }
            const expect = computeBlockHash(b);
            if (b.hash !== expect) {
                return { valid: false, index: i, reason: "hash mismatch" };
            }
            // Kiểm tra chữ ký block GIVE/REVOKE
            if (b.type === "GIVE") {
                const msg = { action: "GIVE", scopes: b.payload.scopes, expiry: b.payload.expiry, dataHash: b.payload.dataHash };
                const mhash = messageHashHex(msg);
                if (!verifySig(b.subjectPubKey, mhash, b.signature)) {
                    return { valid: false, index: i, reason: "invalid signature GIVE" };
                }
            }
            if (b.type === "REVOKE") {
                const msg = { action: "REVOKE", consentId: b.payload.consentId };
                const mhash = messageHashHex(msg);
                if (!verifySig(b.subjectPubKey, mhash, b.signature)) {
                    return { valid: false, index: i, reason: "invalid signature REVOKE" };
                }
            }
        }
        return { valid: true };
    }

    statusOf(consentId) {
        let history = [];
        for (const b of this.blocks) {
            if (b.type === "GIVE" && b.payload.consentId === consentId) history.push(b);
            if (b.type === "REVOKE" && b.payload.consentId === consentId) history.push(b);
        }
        history.sort((a, b) => a.index - b.index);
        if (history.length === 0) return { found: false, active: false, history: [] };
        const last = history[history.length - 1];
        const active = last.type === "GIVE";
        return { found: true, active, history };
    }

    // consentId = sha256( messageHash + pubKey )
    buildConsentId(subjectPubKey, giveMsgHashHex) {
        return sha256Hex(giveMsgHashHex + subjectPubKey);
    }

    async addGive({ scopes, expiry, dataHash, subjectPubKey, signature }) {
        // Verify signature trước khi ghi
        const msg = { action: "GIVE", scopes, expiry, dataHash };
        const mhash = messageHashHex(msg);
        if (!verifySig(subjectPubKey, mhash, signature)) {
            throw new Error("Invalid signature for GIVE");
        }
        const consentId = this.buildConsentId(subjectPubKey, mhash);

        const prev = this.tip();
        const block = {
            index: prev.index + 1,
            timestamp: new Date().toISOString(),
            type: "GIVE",
            payload: { consentId, scopes, expiry, dataHash },
            subjectPubKey,
            signature,
            prevHash: prev.hash
        };
        block.hash = computeBlockHash(block);
        this.blocks.push(block);
        await this.save();
        return { consentId, block };
    }

    async addRevoke({ consentId, subjectPubKey, signature }) {
        const msg = { action: "REVOKE", consentId };
        const mhash = messageHashHex(msg);
        if (!verifySig(subjectPubKey, mhash, signature)) {
            throw new Error("Invalid signature for REVOKE");
        }
        // Check đang active
        const st = this.statusOf(consentId);
        if (!st.found || !st.active) throw new Error("Consent not active or not found");

        const prev = this.tip();
        const block = {
            index: prev.index + 1,
            timestamp: new Date().toISOString(),
            type: "REVOKE",
            payload: { consentId },
            subjectPubKey,
            signature,
            prevHash: prev.hash
        };
        block.hash = computeBlockHash(block);
        this.blocks.push(block);
        await this.save();
        return { consentId, block };
    }
}
