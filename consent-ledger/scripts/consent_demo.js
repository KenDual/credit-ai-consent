import { newPrivateKeyHex, getPublicKeyHex, messageHashHex, signHex } from "../src/crypto.js";

const API = "http://127.0.0.1:3030";

async function postJson(path, body) {
    const res = await fetch(`${API}${path}`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
    });
    const j = await res.json();
    if (!res.ok) throw new Error(JSON.stringify(j));
    return j;
}

async function main() {
    const priv = newPrivateKeyHex();
    const pub = getPublicKeyHex(priv);
    console.log("DEMO WALLET\n", { priv, pub }, "\n");

    // GIVE
    const giveMsg = { action: "GIVE", scopes: "sms,ecom,web", expiry: Math.floor(Date.now() / 1000) + 3600, dataHash: "demoHash" };
    const giveHash = messageHashHex(giveMsg);
    const giveSig = await signHex(priv, giveHash);

    const giveRes = await postJson("/consents/give", {
        scopes: giveMsg.scopes,
        expiry: giveMsg.expiry,
        dataHash: giveMsg.dataHash,
        subjectPubKey: pub,
        signature: giveSig
    });
    console.log("GIVE RESULT\n", giveRes, "\n");

    const cid = giveRes.consentId;

    // STATUS
    let st = await fetch(`${API}/consents/${cid}/status`).then(r => r.json());
    console.log("STATUS AFTER GIVE\n", st, "\n");

    // REVOKE
    const revokeMsg = { action: "REVOKE", consentId: cid };
    const revokeHash = messageHashHex(revokeMsg);
    const revokeSig = await signHex(priv, revokeHash);

    const revokeRes = await postJson("/consents/revoke", {
        consentId: cid,
        subjectPubKey: pub,
        signature: revokeSig
    });
    console.log("REVOKE RESULT\n", revokeRes, "\n");

    st = await fetch(`${API}/consents/${cid}/status`).then(r => r.json());
    console.log("STATUS AFTER REVOKE\n", st, "\n");
}

main().catch(e => { console.error(e); process.exitCode = 1; });