// Shared "Challenge stats" popup — one implementation usable from anywhere:
// the Ear Training screen's own "Stats" button (earTrainingUI.ts) and the
// More sheet's "Challenge stats" row (ui.ts). Ported out of EarTrainingUI's
// former private statsOverlay() so the per-kind score summary lives in
// exactly one place. Mirrors Android's standalone EarStatsDialog(state,
// onDismiss) composable (EarTrainingScreen.kt), which likewise takes only
// AppState — no dependency on the Ear Training screen's own runtime state.

import { AppState, ChallengeScore } from "./appState";
import { el, btn } from "./dom";

const KIND_LABEL: Record<string, string> = {
  progression: "Progressions",
  note2chord: "Note→Chord",
  flavor: "Flavor",
  inversions: "Inversions",
  augdim: "Aug / Dim",
  intervals: "Intervals",
};

/** Full-screen scrim + card listing per-kind runs.
 *
 *  READ-ONLY on purpose: the delete buttons used to sit a thumb-width from a score you
 *  had just set, and hitting one wiped a history that took weeks to build. Deleting now
 *  lives in Settings -> Data, behind a confirm. */
export function renderChallengeStatsOverlay(state: AppState, onClose: () => void): HTMLElement {
  const body = el("div", { class: "et-card", style: "max-width:460px;max-height:80vh;overflow:auto;margin:auto" });
  body.addEventListener("click", (e) => e.stopPropagation());

  const rebuild = () => {
    body.replaceChildren();
    const header = el("div", { style: "display:flex;align-items:center;gap:8px;margin-bottom:6px" }, [
      el("div", { style: "font-weight:700;font-size:16px;flex:1" }, ["Challenge stats"]),
    ]);
    const scores = state.challengeScores;
    body.appendChild(header);

    if (!scores.length) {
      body.appendChild(el("div", { class: "et-muted" }, ["No completed challenges yet — finish any 10-question challenge and it lands here."]));
    } else {
      const byKind = new Map<string, ChallengeScore[]>();
      for (const s of scores) {
        const k = s.kind ?? "progression";
        if (!byKind.has(k)) byKind.set(k, []);
        byKind.get(k)!.push(s);
      }
      for (const [kind, rows] of byKind) {
        const best = rows[0]; // stored best-first per kind
        const avg = Math.round(rows.reduce((a, r) => a + (r.score * 100) / r.total, 0) / rows.length);
        body.appendChild(el("div", { style: "font-weight:700;color:var(--act);margin-top:10px" },
          [KIND_LABEL[kind] ?? kind]));
        body.appendChild(el("div", { class: "et-muted", style: "margin-bottom:4px" }, [
          `best ${best.score}/${best.total}  ·  avg ${avg}%  ·  ${rows.length} run${rows.length === 1 ? "" : "s"}`,
        ]));
        for (const r of rows) {
          const pct = Math.round((r.score * 100) / r.total);
          const secs = (r.durationMs / 1000).toFixed(1);
          body.appendChild(el("div", { style: "font-size:13px;padding:2px 0" }, [
            `${r.score}/${r.total} (${pct}%)  ·  ${secs}s  ·  ${new Date(r.dateMillis).toLocaleDateString()}`,
          ]));
        }
      }
      body.appendChild(el("div", { class: "et-muted", style: "font-size:12px;margin-top:8px" }, [
        "To delete runs, open Settings → Data.",
      ]));
    }
    body.appendChild(el("div", { style: "margin-top:12px;text-align:right" }, [btn("Close", onClose, "btn primary")]));
  };

  rebuild();
  const scrim = el("div", { style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:50" }, [body]);
  scrim.addEventListener("click", onClose);
  return scrim;
}
