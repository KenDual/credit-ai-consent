import { promises as fs } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = resolve(__dirname, "..", "data");
const LEDGER_PATH = resolve(DATA_DIR, "ledger.json");

export async function ensureStorage() {
    try { await fs.mkdir(DATA_DIR, { recursive: true }); } catch { }
    try { await fs.access(LEDGER_PATH); }
    catch {
        // Tạo genesis block
        const genesis = {
            blocks: [
                {
                    index: 0,
                    timestamp: new Date().toISOString(),
                    type: "GENESIS",
                    payload: {},
                    subjectPubKey: "",
                    signature: "",
                    prevHash: "0".repeat(64),
                    hash: "0".repeat(64)
                }
            ]
        };
        await fs.writeFile(LEDGER_PATH, JSON.stringify(genesis, null, 2));
    }
}

export async function loadLedgerFile() {
    const raw = await fs.readFile(LEDGER_PATH, "utf-8");
    return JSON.parse(raw);
}

export async function saveLedgerFile(ledgerObj) {
    await fs.writeFile(LEDGER_PATH, JSON.stringify(ledgerObj, null, 2));
}

export { LEDGER_PATH };
