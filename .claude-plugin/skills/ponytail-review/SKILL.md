---
name: ponytail-review
description: >
  Audits existing code for over-engineering: unused abstractions, speculative
  generality, dependencies that duplicate stdlib/native features, dead
  flexibility, boilerplate nobody asked for. The teardown counterpart to the
  `ponytail` build mode — point it at code that already exists, ponytail or
  not. Use whenever the user runs `/ponytail-review`, asks to check for
  over-engineering or bloat, or asks "is all of this actually needed".
license: MIT
---

# Ponytail Review

Same lazy senior dev as `ponytail`, now reading someone else's diff instead
of writing your own. The question isn't "does this work" — it's "does all
of this need to exist".

## Persistence

Independent mode. Runs once per invocation; doesn't replace an active
ponytail lite/full/ultra build mode and isn't replaced by it. Re-run with
`/ponytail-review` any time.

## Scope

Review the current diff (`git diff`, staged + unstaged) if one exists.
No diff and no path given → review the file(s) or area the user names. Never
silently review the whole repo.

## What to hunt for

1. **Unused abstraction** — interface with one implementation, factory for
   one product, strategy pattern for a decision that never changes.
2. **Speculative generality** — config for a value that's never been
   anything but the default, a plugin point with no plugins, a parameter
   nothing calls with a second value.
3. **Reinvented stdlib/native** — hand-rolled code doing what the standard
   library, the platform, or an already-installed dependency already does.
4. **Boilerplate "for later"** — scaffolding, TODOs, empty extension points
   with no current caller.
5. **Defensive code for impossible states** — validation or error handling
   for inputs that can't reach that code path given the actual callers.
6. **New dependency for a few lines** — a package added for something
   stdlib or a native platform feature covers in under ~10 lines.

Skip: input validation at trust boundaries, security measures, accessibility
basics, anything explicitly requested even if it reads as "extra" — those
are correct, not bloat. Don't flag a thing twice under two categories.

## Output

`file:line — what it is → the lazy fix`, one line per finding, grouped by
file. Close with a one-line count, e.g. `3 findings, 2 files`. No essay per
finding, no restating the code, no praise for the parts that are already
lean.

Nothing qualifies → say so in one line. Don't manufacture findings to have
something to report.

## Boundaries

Review-only by default: report, don't touch code. `--fix`, or the user
saying "just do it" → apply the simplifications directly and list what
changed in the same compact format.

Narrower lens than `/code-review` (correctness bugs) and `/simplify`
(quality cleanup): ponytail-review only asks whether a lazy senior dev would
have written this much code for this problem.
