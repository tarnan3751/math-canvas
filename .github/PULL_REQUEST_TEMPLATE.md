## What and why

<!-- What the change does for the user or the code, and why. Link the issue it closes. -->

## Tier

<!-- A (engine semantics, grammar, persistence, a corpus or property tolerance: agreed in an
     issue first), B (a feature inside the existing frameworks) or C (docs, comments, config).
     See CONTRIBUTING.md, "How work is reviewed". -->

## Checklist

- [ ] `tools/gates.sh` passes; its last lines are below.
- [ ] Every commit is a Conventional Commit: `type(scope): Summary`, imperative, sentence case,
      no trailing period, at most 72 characters, a body saying what and why, no trailers.
- [ ] One logical change per commit, refactors separate from features.
- [ ] Tests come with the change (and property tests and corpus rows for an engine rule).
- [ ] The documents the change makes false are updated in the same change.
- [ ] For a visible change, `tools/ui_tests.sh` passes and a screenshot is attached.

## Gate

```
<!-- the tail of tools/gates.sh -->
```
