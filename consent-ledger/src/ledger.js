// src/ledger.js
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

// Tìm block GIVE đầu tiên tương ứng consentId
function findGrant(blocks, consentId) {
    return blocks.find(b => b.type === "GIVE" && b.payload?.consentId === consentId);
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
        // Kiểm tra liên kết chuỗi + hash + chữ ký + revoke signer khớp grant
        for (let i = 1; i < this.blocks.length; i++) {
            const b = this.blocks[i];
            const prev = this.blocks[i - 1];

            // Liên kết
            if (b.prevHash !== prev.hash) {
                return { valid: false, index: i, reason: "prevHash mismatch" };
            }
            // (tuỳ chọn) chỉ số tăng dần
            if (typeof b.index === "number" && typeof prev.index === "number" && b.index !== prev.index + 1) {
                return { valid: false, index: i, reason: "index not continuous" };
            }
            // Hash nội dung
            const expect = computeBlockHash(b);
            if (b.hash !== expect) {
                return { valid: false, index: i, reason: "hash mismatch" };
            }

            // Xác minh chữ ký
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
                // Người REVOKE phải trùng người GIVE
                const grant = findGrant(this.blocks, b.payload.consentId);
                if (!grant) {
                    return { valid: false, index: i, reason: "grant not found for revoke" };
                }
                if (grant.subjectPubKey !== b.subjectPubKey) {
                    return { valid: false, index: i, reason: "revoke signer mismatch" };
                }
            }
        }
        return { valid: true };
    }

    statusOf(consentId) {
        const history = this.blocks
            .filter(b => (b.type === "GIVE" || b.type === "REVOKE") && b.payload?.consentId === consentId)
            .sort((a, b) => a.index - b.index);

        if (history.length === 0) return { found: false, active: false, history: [] };

        const last = history[history.length - 1];
        const grant = findGrant(history, consentId);
        const nowSec = Math.floor(Date.now() / 1000);

        let active = last.type === "GIVE";
        let reason = undefined;

        if (active) {
            const exp = last.payload?.expiry ?? grant?.payload?.expiry;
            if (typeof exp === "number" && exp < nowSec) {
                active = false;
                reason = "expired";
            }
        } else {
            reason = "revoked";
        }

        return {
            found: true,
            active,
            reason,
            scope: grant?.payload?.scopes ?? null,
            expiry: grant?.payload?.expiry ?? null,
            txHash: last.hash,
            subjectPubKey: grant?.subjectPubKey ?? null,
            history
        };
    }

    // consentId = sha256( messageHash + pubKey )
    buildConsentId(subjectPubKey, giveMsgHashHex) {
        return sha256Hex(giveMsgHashHex + subjectPubKey);
    }

    async addGive({ scopes, expiry, dataHash, subjectPubKey, signature }, opts = {}) {
        const { skipVerify = false } = opts;

        // Verify chữ ký (trừ khi skipVerify dành cho demo)
        const msg = { action: "GIVE", scopes, expiry, dataHash };
        const mhash = messageHashHex(msg);
        if (!skipVerify && !verifySig(subjectPubKey, mhash, signature)) {
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

    async addRevoke({ consentId, subjectPubKey, signature }, opts = {}) {
        const { skipVerify = false } = opts;

        // Tìm grant để ràng buộc người ký revoke = người ký grant
        const grant = findGrant(this.blocks, consentId);
        if (!grant) throw new Error("Consent not found");

        const msg = { action: "REVOKE", consentId };
        const mhash = messageHashHex(msg);

        if (!skipVerify) {
            if (subjectPubKey !== grant.subjectPubKey) {
                throw new Error("Revoke signer mismatch");
            }
            if (!verifySig(subjectPubKey, mhash, signature)) {
                throw new Error("Invalid signature for REVOKE");
            }
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

    getProof(consentId) {
        const history = this.blocks
            .filter(b => (b.type === "GIVE" || b.type === "REVOKE") && b.payload?.consentId === consentId)
            .sort((a, b) => a.index - b.index);

        if (!history.length) return { ok: false, error: "consentId not found", blocks: [] };

        // Xác nhận chuỗi con proof liên kết đúng (prevHash -> hash)
        for (let i = 1; i < history.length; i++) {
            if (history[i].prevHash !== history[i - 1].hash) {
                return { ok: false, error: "broken proof chain", blocks: history };
            }
        }
        return { ok: true, blocks: history };
    }
}
