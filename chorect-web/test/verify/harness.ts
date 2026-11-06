// Shared pass/fail bookkeeping for the verify areas. The runner (../verify.ts) owns the
// exit code and the final "N passed, M failed" line that CI greps for.

export const stats = { passed: 0, failed: 0, area: "" };

export function check(name: string, cond: boolean): void {
  if (cond) { stats.passed++; }
  else { stats.failed++; console.error(`  ✗ [${stats.area}] ${name}`); }
}
