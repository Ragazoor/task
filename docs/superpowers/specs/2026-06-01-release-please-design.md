# release-please for typed-future — Design

**Task:** #1437 — Use release-please
**Date:** 2026-06-01
**Status:** Approved design, pending spec review

## Goal

Automate version management and releases for the `typed-future` library (artifact
`task`, published to Maven Central) using [release-please](https://github.com/googleapis/release-please)
driven by Conventional Commits.

Desired developer experience:

- Contributors land PRs with Conventional Commit titles (`feat:`, `fix:`, …).
- release-please reads those commits on `main` and maintains a **Release PR** that
  bumps the semver version and updates a `CHANGELOG.md`.
- Merging the Release PR cuts a `vX.Y.Z` GitHub release + tag.
- The existing `sbt ci-release` flow publishes that exact version to Maven Central.

## Current state

- Scala library built with sbt; published via `sbt-ci-release` (1.9.3) + sbt-dynver.
  **The version is derived from the git tag** (`git describe`), not stored in a file.
  Latest tag: `v0.1.18`.
- `.github/workflows/ci.yml` has jobs: `lint`, `test`, `ci` (gate), `publish`.
  - Triggers: `pull_request`, `push: [main]`, `release: [published]`.
  - `publish` runs `sbt ci-release` on every **non-PR** event:
    - push to `main` (untagged) → publishes a `-SNAPSHOT`.
    - tagged release → publishes the release version.
  - Secrets used: `PGP_PASSPHRASE`, `PGP_SECRET`, `SONATYPE_PASSWORD`, `SONATYPE_USERNAME`.
- `README.md` hardcodes the version in the install snippet (`% "task" % "0.1.18"`).
- Scala Steward is active (badge in README) but has **no config file**, so it emits
  default, non-conventional commit messages like `Update munit to 1.2.1`.

## Key design decision: avoid the GITHUB_TOKEN event-chaining trap

A release created with the default `GITHUB_TOKEN` does **not** trigger other workflows
(GitHub's anti-recursion rule). This applies to all events triggered by `GITHUB_TOKEN`,
including tag pushes. So we **cannot** rely on release-please's release/tag firing the
existing `publish` job.

**Chosen approach (A): single workflow, conditional publish.** release-please and the
publish steps live in one workflow. When release-please creates a release it sets
`release_created=true`; a downstream job gated on that output runs `sbt ci-release` in
the *same* workflow run. No event chaining, so the default `GITHUB_TOKEN` is sufficient
and **no PAT or GitHub App is required**. Only the existing Sonatype/PGP secrets are needed.

(Rejected alternative B: give release-please a GitHub App token so `release: published`
fires the existing job. More robust token model but adds GitHub App setup; unnecessary
for a personal library. PAT is the discouraged quick version of B.)

### Snapshot behaviour change (accepted)

Moving publishing under `if: release_created` means ordinary merges to `main` no longer
publish `-SNAPSHOT` artifacts. Only Release-PR merges publish. This is an accepted change.

## Components

### 1. release-please configuration (manifest mode)

`release-please-config.json`:

- `release-type: simple` — language-agnostic. We do **not** need release-please to edit
  `build.sbt`, because the sbt version comes from the git tag via dynver.
- `include-component-in-tag: false` — keeps tags as `vX.Y.Z`, matching existing tags and
  what dynver/`sbt-ci-release` expect.
- Changelog `changelog-sections` configured for common conventional types (`feat`, `fix`,
  `chore`/deps, `docs`, `perf`, etc.).
- `extra-files` entry for `README.md` so the documented version is auto-bumped (see #2).

`.release-please-manifest.json`:

- Seeded with `{ ".": "0.1.18" }` so release-please bootstraps from the current version.
  The `v0.1.18` tag already exists, so the next bump is computed from there.

### 2. README version auto-bump

Annotate the install line so release-please keeps it current:

```sbt
libraryDependencies += "io.github.ragazoor" %% "task" % "0.1.18" // x-release-please-version
```

Wired via the `extra-files` generic updater in the config. The annotation renders as a
normal sbt comment.

### 3. New workflow `.github/workflows/release-please.yml`

Trigger: `push: branches: [main]`.

- Job `release-please`:
  - Runs `googleapis/release-please-action@v4` with the default `GITHUB_TOKEN`.
  - Maintains the Release PR; on merge creates the tag + release.
  - Exposes outputs: `release_created`, `tag_name`.
- Job `publish`:
  - `needs: release-please`
  - `if: needs.release-please.outputs.release_created == 'true'`
  - Steps moved from the old `publish` job: checkout with `fetch-depth: 0` (dynver needs
    full history + tags), `coursier/setup-action`, `coursier/cache-action`, then
    `sbt ci-release` with the existing `PGP_*` / `SONATYPE_*` secrets.

### 4. Edit `.github/workflows/ci.yml`

- Remove the `publish` job and the `release:` trigger (publishing now lives in
  `release-please.yml`).
- Keep `lint`, `test`, and the `ci` gate on `pull_request` and `push: [main]`.

### 5. PR-title enforcement `.github/workflows/pr-title.yml`

- Runs `amannn/action-semantic-pull-request` on `pull_request`
  (`opened`, `edited`, `synchronize`).
- Fails the check when the PR title is not a valid Conventional Commit.
- **Manual step (documented, not code):** mark this check **required** in branch
  protection / rulesets so a red check actually blocks merge.

### 6. Scala Steward config `.scala-steward.conf`

- Add `commits.message = "chore(deps): update ${artifactName} to ${nextVersion}"` so
  Steward emits conventional commits. This makes Steward PRs (a) pass the PR-title check
  and (b) get grouped under a Dependencies/chore section by release-please.

### 7. Documented manual GitHub settings

Listed in the spec / PR description, not changeable from code:

- Settings → General → Pull Requests → **Default to PR title for squash merge commits**
  (so the squash commit = the validated PR title that release-please parses).
- Mark the PR-title check (and optionally `ci`) as **required** status checks.

## Data flow

```
PR (conventional title) ──squash-merge──> main
        │                                   │
        │                            release-please reads commits
        │                                   │
        │                          maintains/updates Release PR
        │                                   │
        └────────────── merge Release PR ───┘
                                            │
                            release-please cuts vX.Y.Z tag + GitHub release
                                            │
                            same workflow: publish job (release_created)
                                            │
                                 sbt ci-release → Maven Central
```

## Testing plan

1. Open a PR with a `feat:` or `fix:` title; confirm the PR-title check passes (and a
   bad title fails).
2. Merge it; confirm release-please opens/updates a Release PR with the correct semver
   bump and a CHANGELOG entry.
3. Merge the Release PR; confirm the `vX.Y.Z` tag + GitHub release are created and the
   `publish` job runs `sbt ci-release`, publishing that version to Maven Central.
4. Confirm `README.md` version was bumped in the Release PR.

Because publishing to Maven Central is a real side effect, early testing can validate
steps 1–2 (and the Release PR diff for steps 3–4) before the first real merge.

## Out of scope / follow-ups

- Changing the merge strategy (assumed **squash merge**, matching the PR-title goal).
- Migrating historical changelog content.
- Tuning release-please changelog section labels beyond a sensible default set.
