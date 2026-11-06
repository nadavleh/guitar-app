// Area "version" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- version

import { readFileSync } from "node:fs";
import { check } from "./harness";

export function run(): void {

  // --- Version numbers must not drift ---
  // package.json had silently fallen 33 minor versions behind APP_VERSION (2.38.9 vs
  // 2.71.3) despite the "keep in sync" comment on the constant. Read the file rather than
  // importing it: resolveJsonModule is off in tsconfig, and verify runs under Node.
  {
    const read = (rel: string) => readFileSync(new URL(rel, import.meta.url), "utf8");
    const pkgVersion = (JSON.parse(read("../package.json")) as { version: string }).version;
    // Read appState.ts as TEXT rather than importing it: this script runs under plain Node,
    // and importing the app-state module would execute its browser-facing module scope.
    const appVersion = /APP_VERSION\s*=\s*"([^"]+)"/.exec(read("../src/app/appState.ts"))?.[1];
    check(`package.json ${pkgVersion} === APP_VERSION ${appVersion}`, !!appVersion && pkgVersion === appVersion);
  }
}
