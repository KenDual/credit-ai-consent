// scripts/deploy.js
import { network } from "hardhat";

async function main() {
    // Kết nối network được truyền bằng --network (ở đây là localhost)
    const { viem } = await network.connect();

    // Deploy contract
    const consent = await viem.deployContract("ConsentRegistry");
    console.log("ConsentRegistry deployed at:", consent.address);
}

main().catch((err) => {
    console.error(err);
    process.exitCode = 1;
});
