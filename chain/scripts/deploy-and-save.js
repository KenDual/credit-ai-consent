import { network, artifacts } from "hardhat";
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";

async function main() {
    const { viem } = await network.connect();
    const consent = await viem.deployContract("ConsentRegistry");

    const publicClient = await viem.getPublicClient();
    const blockNumber = await publicClient.getBlockNumber();

    const out = {
        network: "localhost",
        contract: "ConsentRegistry",
        address: consent.address,
        blockNumber: blockNumber.toString(),
        timestamp: new Date().toISOString(),
    };

    const outPath = resolve(process.cwd(), "deployed.localhost.json");
    writeFileSync(outPath, JSON.stringify(out, null, 2));
    console.log("Saved:", outPath, "\n", out);
}

main().catch((e) => {
    console.error(e);
    process.exitCode = 1;
});
