// scripts/smoke.js
import { network, artifacts } from "hardhat";
import { parseEventLogs } from "viem";

const ADDRESS = process.env.CONSENT_ADDR || ""; // truyền qua env cho tiện

async function main() {
    const { viem } = await network.connect();
    if (!ADDRESS) throw new Error("Set CONSENT_ADDR=0x... or update ADDRESS in scripts/smoke.js");

    const consent = await viem.getContractAt("ConsentRegistry", ADDRESS);

    // Lấy wallet đầu tiên từ hardhat node
    const [wallet] = await viem.getWalletClients();

    // Gọi give()
    const scopes = "sms,ecom,web";
    const expiry = BigInt(Math.floor(Date.now() / 1000) + 3600); // +1h
    const dataHash = "demoHash";

    const txHash = await consent.write.give([scopes, expiry, dataHash], {
        // chỉ định account ký (không bắt buộc nếu dùng ví mặc định)
        account: wallet.account,
    });
    console.log("txHash:", txHash);

    // Đợi receipt rồi parse event logs
    const publicClient = await viem.getPublicClient();
    const receipt = await publicClient.waitForTransactionReceipt({ hash: txHash });

    const { abi } = await artifacts.readArtifact("ConsentRegistry");
    const decoded = parseEventLogs({
        abi,
        logs: receipt.logs,
        eventName: "ConsentGiven",
    });

    if (decoded.length) {
        const ev = decoded[0];
        console.log("ConsentGiven event:");
        console.log("  consentId:", ev.args.consentId);
        console.log("  subject:", ev.args.subject);
        console.log("  scopes:", ev.args.scopes);
        console.log("  expiry:", ev.args.expiry);
        console.log("  dataHash:", ev.args.dataHash);
    } else {
        console.log("No ConsentGiven event found.");
    }
}

main().catch((e) => {
    console.error(e);
    process.exitCode = 1;
});
