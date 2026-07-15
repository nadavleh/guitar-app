// Tiny DOM construction helpers — enough to build the UI without a framework.

type Child = Node | string | null | undefined | false;

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  children: Child[] = [],
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") node.className = v;
    else node.setAttribute(k, v);
  }
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    node.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
  }
  return node;
}

export function clear(node: HTMLElement): void {
  while (node.firstChild) node.removeChild(node.firstChild);
}

/** A button with text + click handler. */
export function btn(text: string, onClick: () => void, cls = "btn"): HTMLButtonElement {
  const b = el("button", { class: cls }, [text]);
  b.addEventListener("click", onClick);
  return b;
}

/** A single-choice segmented control. */
export function segmented<T extends string>(
  options: { value: T; label: string }[],
  selected: T,
  onSelect: (v: T) => void,
  full = true,
): HTMLElement {
  const row = el("div", { class: full ? "seg full" : "seg" });
  for (const opt of options) {
    const b = el("button", { class: opt.value === selected ? "selected" : "" }, [opt.label]);
    b.addEventListener("click", () => onSelect(opt.value));
    row.appendChild(b);
  }
  return row;
}

/** A wrapping row of filter chips. */
export function chipRow<T>(
  options: { value: T; label: string }[],
  isSelected: (v: T) => boolean,
  onSelect: (v: T) => void,
): HTMLElement {
  const row = el("div", { class: "chip-row" });
  for (const opt of options) {
    const b = el("button", { class: isSelected(opt.value) ? "chip selected" : "chip" }, [opt.label]);
    b.addEventListener("click", () => onSelect(opt.value));
    row.appendChild(b);
  }
  return row;
}

/** A labeled slider that reports its live value. */
export function slider(min: number, max: number, value: number, onInput: (v: number) => void, step = 1): HTMLInputElement {
  const s = el("input", { type: "range", min: String(min), max: String(max), step: String(step), value: String(value) });
  // Throttle to one state update per animation frame: a drag fires "input" per
  // pixel, and each update triggers a full re-render pass upstream.
  let raf = 0;
  s.addEventListener("input", () => {
    if (raf) return;
    raf = requestAnimationFrame(() => { raf = 0; onInput(parseFloat(s.value)); });
  });
  // Always deliver the final value on release.
  s.addEventListener("change", () => {
    if (raf) { cancelAnimationFrame(raf); raf = 0; }
    onInput(parseFloat(s.value));
  });
  return s;
}

/** A toggle switch with a label + optional sub-text. */
export function switchRow(label: string, sub: string | null, checked: boolean, onChange: (v: boolean) => void): HTMLElement {
  const input = el("input", { type: "checkbox" });
  input.checked = checked;
  input.addEventListener("change", () => onChange(input.checked));
  const sw = el("label", { class: "switch" }, [input, el("span", { class: "track" }), el("span", { class: "thumb" })]);
  const text = el("div", { class: "switch-text" }, [el("div", {}, [label]), sub ? el("div", { class: "sub" }, [sub]) : null]);
  return el("div", { class: "switch-row" }, [text, sw]);
}

export function labelSm(text: string): HTMLElement {
  return el("span", { class: "label-sm" }, [text]);
}

/** A song list row: the "Title — Artist" text is a link that opens a YouTube search
 *  (default, new tab); a Spotify glyph opens the same query on Spotify (alternative);
 *  plus a copy button that puts "Title — Artist" on the clipboard so you can search
 *  yourself. `extra` is appended to the label (e.g. " (key A)"). */
export function songLinkRow(title: string, artist: string, extra = ""): HTMLElement {
  const label = `${title} — ${artist}${extra}`;
  const q = encodeURIComponent(`${title} ${artist}`);
  const link = el("a", {
    href: `https://www.youtube.com/results?search_query=${q}`,
    target: "_blank",
    rel: "noopener",
    style: "flex:1;color:inherit;text-decoration:none;cursor:pointer",
    title: "Search on YouTube",
  }, [`▶  ${label}`]);
  // Spotify alternative — search the same query on open.spotify.com.
  const spotify = el("a", {
    href: `https://open.spotify.com/search/${q}`,
    target: "_blank",
    rel: "noopener",
    class: "btn text",
    style: "padding:0 6px;min-width:0;color:#1DB954;text-decoration:none;font-weight:700",
    title: "Search on Spotify",
  }, ["♫"]);
  spotify.addEventListener("click", (e) => e.stopPropagation());
  const copy = el("button", { class: "btn text", style: "padding:0 6px;min-width:0", title: "Copy" }, ["⧉"]);
  copy.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard?.writeText(label);
    const prev = copy.textContent;
    copy.textContent = "✓";
    setTimeout(() => { copy.textContent = prev; }, 900);
  });
  return el("div", { style: "display:flex;align-items:center;gap:6px;font-size:14px;padding:2px 0" }, [link, spotify, copy]);
}
