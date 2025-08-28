// src/crypto.js
import { createHash, createHmac, randomBytes } from "node:crypto";
import * as secp from "@noble/secp256k1";

// ---- Helpers hex <-> bytes (không dùng utils.* của noble)
const strip0x = (h) => (typeof h === "string" && h.startsWith("0x") ? h.slice(2) : h);
export const hexToBytes = (hex) => Uint8Array.from(Buffer.from(strip0x(hex), "hex"));
export const bytesToHex = (bytes) => Buffer.from(bytes).toString("hex");

// ---- Cấp HMAC/SHA256 & random cho noble (sửa lỗi hmacSha256Sync not set)
const hmacSha256Sync = (key, ...msgs) => {
    const h = createHmac("sha256", Buffer.from(key));
    for (const m of msgs) h.update(Buffer.from(m));
    return new Uint8Array(h.digest());
};
const sha256Sync = (...msgs) => {
    const h = createHash("sha256");
    for (const m of msgs) h.update(Buffer.from(m));
    return new Uint8Array(h.digest());
};
secp.etc.hmacSha256Sync = hmacSha256Sync;
secp.etc.sha256Sync = sha256Sync;
secp.etc.randomBytes = (len) => new Uint8Array(randomBytes(len));

// ---- Hash tiện ích
export function sha256Hex(input) {
    const h = createHash("sha256");
    h.update(typeof input === "string" ? input : Buffer.from(input));
    return h.digest("hex");
}

// ---- Chuẩn hoá JSON để hash/verify ổn định
export function canonicalize(obj) {
    if (Array.isArray(obj)) return `[${obj.map(canonicalize).join(",")}]`;
    if (obj && typeof obj === "object") {
        const keys = Object.keys(obj).sort();
        return `{${keys.map(k => JSON.stringify(k) + ":" + canonicalize(obj[k])).join(",")}}`;
    }
    return JSON.stringify(obj);
}
export function messageHashHex(messageObj) {
    return sha256Hex(canonicalize(messageObj));
}

// ---- Ký & verify (không dùng utils.hexToBytes)
export async function signHex(privKeyHex, msgHashHex) {
    const msgBytes = hexToBytes(msgHashHex);
    const privBytes = hexToBytes(privKeyHex);

    // Noble v2 có thể trả Signature object → chuyển về 64-byte raw
    const sig = await secp.sign(msgBytes, privBytes);
    let raw;
    if (sig instanceof Uint8Array) raw = sig;
    else if (typeof sig.toCompactRawBytes === "function") raw = sig.toCompactRawBytes();
    else if (typeof sig.toRawBytes === "function") raw = sig.toRawBytes();
    else throw new Error("Unexpected Signature type from noble-secp256k1");
    return bytesToHex(raw);
}
export function verifySig(pubKeyHex, msgHashHex, sigHex) {
    return secp.verify(hexToBytes(sigHex), hexToBytes(msgHashHex), hexToBytes(pubKeyHex));
}

// ---- Khoá tiện ích
export function newPrivateKeyHex() {
    return bytesToHex(secp.utils.randomPrivateKey());
}
export function getPublicKeyHex(privKeyHex) {
    // trả về compressed (33 bytes, bắt đầu 02/03)
    return bytesToHex(secp.getPublicKey(hexToBytes(privKeyHex), true));
}
