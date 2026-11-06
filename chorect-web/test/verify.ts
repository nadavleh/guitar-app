// Runtime sanity checks for the ported theory + audio engines. Built as an SSR
// bundle (`vite build --ssr`) and run under Node, so we exercise the real modules
// the browser uses. Pins the TS ports against the Kotlin ones.
//
// The checks live in test/verify/<area>.ts. No args runs every area:
//   npm run verify
// Names run only those areas (the build still bundles everything):
//   npm run verify -- cavaq chords
// Exit code is non-zero on any failed check, an unknown area, or a crashed area.

import { stats } from "./verify/harness";
import { run as audio } from "./verify/audio";
import { run as caged } from "./verify/caged";
import { run as cavaq } from "./verify/cavaq";
import { run as chords } from "./verify/chords";
import { run as drums } from "./verify/drums";
import { run as ear } from "./verify/ear";
import { run as intervals } from "./verify/intervals";
import { run as songs } from "./verify/songs";
import { run as theory } from "./verify/theory";
import { run as version } from "./verify/version";

const AREAS: Record<string, () => void> = {
  audio, caged, cavaq, chords, drums, ear, intervals, songs, theory, version,
};

const requested = process.argv.slice(2).filter((a) => !a.startsWith("-"));
const unknown = requested.filter((a) => !(a in AREAS));
if (unknown.length > 0) {
  console.error(`unknown area(s): ${unknown.join(", ")}\navailable: ${Object.keys(AREAS).join(", ")}`);
  process.exit(2);
}

for (const name of requested.length > 0 ? requested : Object.keys(AREAS)) {
  stats.area = name;
  try {
    AREAS[name]();
  } catch (e) {
    stats.failed++;
    console.error(`  ✗ [${name}] area crashed: ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`);
  }
}

console.log(`\n${stats.passed} passed, ${stats.failed} failed`);
process.exit(stats.failed === 0 ? 0 : 1);
