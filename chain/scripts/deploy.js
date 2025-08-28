// chain/scripts/deploy.js
const hre = require("hardhat");

async function main() {
    const ConsentRegistry = await hre.ethers.getContractFactory("ConsentRegistry");
    const consent = await ConsentRegistry.deploy();
    await consent.waitForDeployment();
    const addr = await consent.getAddress();
    console.log("ConsentRegistry deployed to:", addr);
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
