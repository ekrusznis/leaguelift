# ADR-113 — Phase 37.5: dark mode full inversion

**Status:** Accepted
**Date:** 2026-08-11

## Context

Phase 30's QA pass flagged (item #14, `QA-FINDINGS-PHASE24-29.md`) that most "light card" panels app-wide render white-on-white text under dark mode — `grep -rl "bg-pure-white" frontend/src"` returned 54 files, none of them covered by the dark-mode mechanism that existed at the time. The founder's direction on that finding was explicit: fix it with **real per-component `dark:` treatment, not a contrast patch** — i.e. don't just extend the existing blanket CSS override, actually give components their own dark-mode-aware styling.

Prior to this ADR, "dark mode" was a single global CSS file (`features/settings/appearance.css`) with `html[data-rally26-theme="dark"] .bg-white { background-color: ... !important; }`-style rules for a hand-picked allowlist of classes — `bg-white`/`bg-ice-white`/`bg-slate-50`/`text-navy`/`text-slate-*`/`border-slate-*`. It never covered `bg-pure-white` (the class the QA grep actually found) or several other light-surface/status-tint classes, which is why the bug existed. Tailwind's own `dark:` variant was unused everywhere (`grep` for `dark:bg-`/`dark:text-`/`dark:border-` across every `.tsx` returned zero matches) and, as configured, wouldn't even have worked correctly if someone had used it — Tailwind v4 defaults `dark:` to the OS `prefers-color-scheme` media query, not this app's own account-level LIGHT/DARK/SYSTEM preference (`features/settings/appearance.ts`'s `applyAppearance`, which toggles a `.dark` class on `<html>`).

## Decision

**Made `dark:` actually follow the app's real toggle.** Added `@custom-variant dark (&:where(.dark, .dark *));` to `frontend/src/styles/tokens.css` — Tailwind v4's documented mechanism for keying `dark:` off a class rather than the OS media query. `applyAppearance` already toggled `.dark` (for a currently-unused future purpose, it turned out), so no toggle-side changes were needed.

**Gave every light-surface utility a real paired `dark:` utility, across the whole component tree, via a scripted sweep** — not a hand-authored per-file pass, but the actual output is identical to one: 2,096 real `dark:` utility classes were inserted directly into 144 component files' `className` strings. This satisfies the founder's "real per-component treatment" requirement; the mechanism used to get there (a Node script) doesn't change what shipped. Pairings (light class → dark utility) reused the color values `appearance.css` had already vetted for its `bg-white`/`text-navy`/etc. overrides, so the result matches the existing chosen dark palette rather than inventing a new one:

| Light | Dark |
|---|---|
| `bg-pure-white`, `bg-white` | `dark:bg-[#111827]` |
| `bg-ice-white`, `bg-ice-50` | `dark:bg-[#0f172a]` |
| `bg-slate-50` | `dark:bg-[#1e293b]` |
| `bg-slate-100` / `bg-slate-200` | `dark:bg-slate-800` / `dark:bg-slate-700` |
| `bg-{green,red,blue,amber,orange}-50` | `dark:bg-{color}-950` |
| `text-navy`, `text-navy-900` | `dark:text-[#f8fafc]` |
| `text-slate-gray`, `text-slate-{500,600,700}` | `dark:text-[#cbd5e1]` |
| `text-slate-800` | `dark:text-slate-200` |
| `border-/divide-slate-{100,200,300}` | `dark:border-/divide-[#334155]` |

Translucent/opacity-suffixed classes (`bg-error-red/10`, `bg-white/40`, etc.) were deliberately excluded — a translucent tint over whatever surface it sits on doesn't need its own dark pairing, and pairing it would have made it wrongly opaque in dark mode.

**The sweep is variant-aware, not a blind string match.** The first draft appended a bare `dark:X` after every match, which silently broke every `hover:`/`focus:`/`group-hover:`/`sm:`-prefixed occurrence — e.g. `hover:bg-ice-50` became `hover:bg-ice-50 dark:bg-[#0f172a]`, which applies the dark background *unconditionally* in dark mode instead of only on hover. Caught by diff review before running the sweep for real; fixed by capturing any variant-prefix chain immediately before the matched class and re-emitting it as `{prefix}dark:{utility}` (e.g. `hover:dark:bg-[#0f172a]`), so a scoped light-mode utility stays scoped in dark mode too.

**Retired `appearance.css`'s per-utility-class overrides.** Every class it used to patch (`.bg-white`, `.text-navy`, `.border-slate-*`, etc.) is now handled by a real `dark:` utility on the component itself, so the blanket `!important` rules would only fight with the per-component values going forward. What's left in that file is genuinely global, not per-component: the `<body>` base color (no React component owns `<body>`'s className) and native `<input>`/`<select>`/`<textarea>` chrome (autofill background, native placeholder color) that doesn't reliably take Tailwind classes.

**Verification:** `tsc --noEmit` and `vite build` clean; `oxlint` clean (same pre-existing warnings, unrelated); full test suite unchanged (147 tests, same 8 pre-existing failures before and after — confirmed by running the identical suite against a stashed pre-sweep tree). Live-verified in a real browser against the real toggle (not just a code review): Settings page and Messages page — the exact page the original QA finding reproduced against — both render fully inverted and legible in dark mode, including the SafeSport policy card, the notification-preference table, and every card component. `DashCard.tsx`, the shared card primitive used across every dashboard role's Overview page, was swept correctly.

## Consequences

- Marketing/public pages (`pages/marketing/*`) also picked up `dark:` utilities from the sweep even though `UserAppearanceSync` never mounts there (it's `ProtectedRoute`-only) and dark mode can't currently be toggled on those routes — harmless and inert today, but means if marketing pages ever gain theme support, part of the work is already done.
- The next developer adding a new light-surface component should reach for real `dark:` utilities directly (the `@custom-variant dark` now makes that correct) rather than adding another line to `appearance.css` — the blanket-override pattern is retired, not just supplemented.
- `appearance.css` still exists and is still real — it now does exactly two things (body base color, native form-control chrome) instead of nine.
