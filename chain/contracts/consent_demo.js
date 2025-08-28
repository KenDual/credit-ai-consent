// chain/scripts/consent_demo.js
const hre = require("hardhat");

async function run() {
    const [owner, alice] = await hre.ethers.getSigners();
    const addr = process.env.CONSENT_ADDR; // set biến môi trường trước khi chạy
    if (!addr) throw new Error("CONSENT_ADDR is not set");

    const consent = await hre.ethers.getContractAt("ConsentRegistry", addr);

    const expiry = Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30; // 30 ngày
    const tx = await consent.connect(alice).grant("sms", expiry, "sha256:demo-terms-hash");
    await tx.wait();

    const res = await consent.check(alice.address, "sms");
    console.log("Alice SMS consent:", res);
}

run().catch((e) => { console.error(e); process.exit(1); });
