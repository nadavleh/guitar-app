// Shared "Challenge stats" popup — one implementation usable from anywhere:
// the Ear Training screen's own "Stats" button (earTrainingUI.ts) and the
// More sheet's "Challenge stats" row (ui.ts). Ported out of EarTrainingUI's
// former private statsOverlay() so the per-kind score summary lives in
// exactly one place. Mirrors Android's standalone EarStatsDialog(state,
// onDismiss) composable (EarTrainingScreen.kt), which likewise takes only
// AppState — no dependency on the Ear Training screen's own runtime state.

import { AppState } from "./appState";
import { el, btn } from "./dom";

const KIND_LABEL: Record<string, string> = {
  progression: "Progressions",
  note2chord: "Note→Chord",
  flavor: "Flavor",
  inversions: "Inversions",
  augdim: "Aug / Dim",
  intervals: "Intervals",
};

/** Full-screen scrim + card listing best/avg/run-count per challenge kind. */
export function renderChallengeStatsOverlay(state: AppState, onClose: () => void): HTMLElement {
  const body = el("div", { class: "et-card", style: "max-width:460px;max-height:75vh;overflow:auto;margin:auto" }, [
    el("div", { style: "font-weight:700;font-size:16px;margin-bottom:6px" }, ["Challenge stats"]),
  ]);
  const scores = state.challengeScores;
  if (!scores.length) {
    body.appendChild(el("div", { class: "et-muted" }, ["No completed challenges yet — finish any 10-question challenge and it lands here."]));
  } else {
    const byKind = new Map<string, typeof scores>();
    for (const s of scores) {
      const k = s.kind ?? "progression";
      if (!byKind.has(k)) byKind.set(k, []);
      byKind.get(k)!.push(s);
    }
    for (const [kind, rows] of byKind) {
      const best = rows[0]; // stored best-first per kind
      const avg = Math.round(rows.reduce((a, r) => a + (r.score * 100) / r.total, 0) / rows.length);
      const last = rows.reduce((a, r) => Math.max(a, r.dateMillis), 0);
      body.appendChild(el("div", { style: `font-weight:700;color:var(--act);margin-top:6px` }, [KIND_LABEL[kind] ?? kind]));
      body.appendChild(el("div", { class: "et-muted" }, [
        `best ${best.score}/${best.total}  ·  avg ${avg}%  ·  ${rows.length} run${rows.length === 1 ? "" : "s"}  ·  last ${new Date(last).toLocaleDateString()}`,
      ]));
    }
  }
  body.appendChild(el("div", { style: "margin-top:10px;text-align:right" }, [btn("Close", onClose, "btn primary")]));
  body.addEventListener("click", (e) => e.stopPropagation());
  const scrim = el("div", { style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:50" }, [body]);
  scrim.addEventListener("click", onClose);
  return scrim;
}
