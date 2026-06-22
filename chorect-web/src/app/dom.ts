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
  s.addEventListener("input", () => onInput(parseFloat(s.value)));
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
