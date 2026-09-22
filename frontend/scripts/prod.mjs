// Build de production puis serveur, dans un dossier distinct de celui de `next dev` (.next-prod) :
// on peut donc garder un serveur de developpement ouvert sur un autre port sans corrompre le build.
// Usage : npm run prod            (port 3000)
//         PORT=3001 npm run prod  (autre port)
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const nextBin = path.join(root, "node_modules", "next", "dist", "bin", "next");
const env = { ...process.env, NEXT_DIST_DIR: ".next-prod", NODE_ENV: "production" };

function run(...args) {
  const result = spawnSync(process.execPath, [nextBin, ...args], { cwd: root, env, stdio: "inherit" });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

run("build");
run("start", "-p", process.env.PORT || "3000");
