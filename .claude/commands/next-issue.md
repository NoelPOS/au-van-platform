---
description: Run one full AU-Van delivery cycle — plan, implement, review, merge, and update the board for the next ready issue.
---

Run one complete delivery cycle for the next ready AU-Van issue.

## Concurrent tracks

Several issues may run at once when their file surfaces are disjoint. Check
before starting: two tracks must not both touch the same package, the same
migration sequence, or any of these shared surfaces.

- `AGENTS.md` and `CLAUDE.md`.
- `.claude/`, including `.claude/settings.json`.
- `.github/` — the workflow files *and* everything beside them, such as
  `pull_request_template.md`.
- `.gitignore`.
- `docs/project-status.md`, `docs/data-model.md`, and `docs/architecture.md`.

These are what the test used to miss; two rule-level tracks are almost never
disjoint. PR #30 and PR #35 ran concurrently and both added to `.gitignore`,
one entry from #30 and thirteen lines from #35.

`docs/adr/` is **not** a shared surface — two tracks may each add an ADR,
because the files differ. The collision there is the sequence number, which
both would otherwise claim. **The orchestrator allocates ADR numbers** and
tells each track which number to use. This has already happened once: #25's
plan drafted its ADR as 007, which #35 holds, and the orchestrator renumbered
it to 008.

Typical safe pairings are an API issue with an infrastructure issue, or an API
issue with a web issue whose endpoints already exist on `main`.

Two rules keep them from colliding:

- **The orchestrator owns `docs/project-status.md`.** Implementers must not
  touch it; every track would otherwise conflict on it. Update it yourself
  after each merge, as its own pull request — see §8.
- **Each track rebases on `main` after any other track merges**, then re-runs
  both CI and the reviewer, because the diff has changed.

When surfaces do overlap, serialise: finish one before starting the other.

## 1. Sync and select

```sh
git checkout main && git pull --ff-only
gh issue list --state open --json number,title,labels,milestone
gh pr list --state open
gh project item-list 1 --owner NoelPOS --format json --limit 50
```

Take the next issue from the ordered backlog in `docs/project-status.md`. An
open pull request does not block a new track, provided the surfaces are
disjoint as described above; when they overlap, finish the open one first.

## 2. Plan

Set the board item to **In progress** (`9bf45f6f`). Run the `issue-planner`
agent.

- `VERDICT: CREDENTIALS-REQUIRED` → go to **Blocked**.
- `VERDICT: ADR-REQUIRED` → keep going; the drafted ADR gets committed.
- `VERDICT: CLEAR` → keep going.

Post the plan: `gh issue comment <n> --body-file <plan>`.

## 3. Implement

Give this track its own worktree, so a concurrent track's `git checkout` cannot
move your branch out from under you:

```sh
git worktree add -b <type>/<kebab-description> .worktrees/<short-name> main
```

`.worktrees/` sits inside the repository, so the agent file tools can write
there, and it is git-ignored. `git worktree` is not in the
`.claude/settings.json` allow list, so the command prompts for approval each
time; adding the entry is the repository owner's call and is tracked in #42.

**Do not `cd` into the worktree and expect to stay there.** An agent's shell
working directory resets to the repository root between every Bash call, so a
`cd` in one call is gone by the next, and every later `git add`, `git commit`
or `npm run` would run in the shared root tree on whatever branch that tree
holds — exactly the collision the worktree exists to prevent. Instead put the
`cd` and the command it applies to in **one compound Bash call**, one call per
operation:

- **Git:** `cd .worktrees/<short-name> && git add <path>`, then
  `cd .worktrees/<short-name> && git commit -m "<subject>"`.
- **The local gate:** the same shape, one call per project.

```sh
cd .worktrees/<short-name>/web && npm ci && npm run lint && npm run test && npm run build
cd .worktrees/<short-name>/api && ./gradlew test
```

- **File tools:** absolute paths under `.worktrees/<short-name>/`. A relative
  path resolves against the root tree, not the worktree.

The compound form is the one the permission matcher accepts. Probed against the
live matcher with a deny entry, `echo probe && git push --force --dry-run …` is
denied — so the matcher **does** decompose an `&&` chain and evaluate each
sub-command. The `git commit` in the second position is therefore matched by
the existing `Bash(git commit:*)` allow entry, and the leading `cd` costs
nothing.

**Do not use `git -C <worktree> …`.** The same probe showed
`git -C <repo> push --force --dry-run …` executing where the bare form was
denied: inserting `-C <path>` before the subcommand defeats prefix matching
entirely. So `git -C .worktrees/<short-name> commit -m …` matches no allow
entry and prompts on every commit. #43 records that matcher gap. Do **not**
propose `Bash(git -C:*)` as the fix either: it would make
`git -C . push --force` and `git -C . config core.hooksPath …` both allowed and
unreachable by the existing denies, which is strictly worse than today.

Work on that branch for the whole cycle, and remove the worktree with
`git worktree remove` once the pull request has merged.

Run the `implementer` agent. It writes code, tests, and docs, commits with
subject-only Conventional Commits, and runs the full local gate.

## 4. Open the pull request

```sh
git push -u origin <branch>
gh pr create --base main --title "<type(scope): outcome>" --body-file <body>
gh project item-add 1 --owner NoelPOS --url <pr-url>
```

Fill `.github/pull_request_template.md`, including `Closes #<n>`. **No tool
attribution in the body.** Set the board item to **In review** (`811cdd56`).

## 5. CI gate

```sh
gh pr checks <n> --watch
```

"Web checks", "API checks", "Commit checks", and "GitGuardian Security Checks"
must all be `SUCCESS`. The first three are required contexts on `main`, so a red
one cannot be merged around. Red → **Failure handling**.

## 6. Review gate

Run the `pr-reviewer` agent and read its final `VERDICT:` line from its output.
Do not look for a GitHub review state — the reviewing account is also the author
here, so `gh pr review --approve` returns 422; the reviewer posts its findings
with `gh pr comment` instead.

Then confirm it left nothing behind:

```sh
git status --porcelain
```

Non-empty means the reviewer mutated the tree — abort the cycle and report it.
`VERDICT: REQUEST_CHANGES` → **Failure handling**. This gate has no exception:
nothing merges until a reviewer run returns `VERDICT: APPROVE`.

## 6b. Confirm the review is on the pull request

The reviewer posts its own findings and verdict, so do not post them again —
a second comment would only duplicate it. Assert that its comment landed:

```sh
gh pr view <n> --json comments \
  --jq '[.comments[] | select(.body | contains("VERDICT:"))] | length'
```

Expect `>= 1`. The count is the running total over every round on this pull
request, not the count for the round you just ran, and you hold no round
counter — so assert only that it is not zero. Zero means the reviewer did not
post: abort the cycle and report it. Reviews that live only in an agent transcript
leave no trail; every claim the loop makes about having been reviewed must be
checkable from GitHub.

## 7. Merge

Re-check that `main` has not moved, then merge:

```sh
gh pr view <n> --json mergeable,mergeStateStatus
gh pr merge <n> --rebase
```

No `--auto` (disabled on this repository). No `--delete-branch` (branches are
kept here).

## 8. Close out and verify

Set the issue's board item to **Done** (`afc01cfd`); close the issue if `Closes
#` did not. Close a parent issue when its last child lands. Then **read the
board back** and assert the statuses are what you set — this step is what keeps
the board from drifting.

- **Land your `docs/project-status.md` update as its own pull request.** Record
  the merge on a `docs/<short-name>` branch and open a pull request for it.
  There is no direct-push route: `main` is protected with administrator
  enforcement, and its required "Commit checks" context runs only on a pull
  request, so a push straight to `main` can never turn that check green.

  That pull request takes the same gates as any other: the CI gate in §5, the
  reviewer gate in §6 and §6b, and `gh pr merge --rebase` in §7. It is the one
  pull request with no issue of its own — it is bookkeeping for a merge that
  has already happened — so `AGENTS.md` and `pr-reviewer.md` exempt it by name
  from the linked-issue requirement. The exemption is narrow: it holds only
  while the diff is `docs/project-status.md` and nothing else. Anything further
  needs its own issue and its own pull request.

## Failure handling

Counters are per issue and reset on merge.

- **CI red.** Infrastructure flake → `gh run rerun --failed` once, not counted.
  Real failure → the implementer fixes it on the same branch with a new
  subject-only commit. **Limit 3.**
- **Review rejected.** The implementer addresses every finding; the reviewer
  runs again from scratch and must return `VERDICT: APPROVE` before the merge.
  That holds for a round whose only findings are description inaccuracies too:
  the author certifying their own corrected description is exactly the defect
  class this loop exists to stop, and re-running the reviewer over a corrected
  body is cheap. Keep going while each round surfaces a **new** defect
  — rounds that are still finding real problems are working, and a raw cap on
  them was tried here and overridden twice because it measured the wrong thing.
  Escalate to **Blocked** when a round finds nothing new but still rejects, when
  a finding recurs after being fixed, or after six rounds, whichever comes
  first.
- **Conflict with `main`.** `git fetch origin && git rebase origin/main`,
  resolve, `git push --force-with-lease`, then re-run **both** CI and the
  reviewer. A conflict in booking or payment logic → **Blocked** immediately;
  never guess at merge semantics for seats or money.
- **`main` goes red after a merge.** Highest priority. Open a `Bug:` issue and
  fix forward; if that does not work in one cycle, revert the merge with
  `git revert --no-commit <sha>` followed by a hand-written
  `revert(scope): …` subject, so the commit convention still holds.
- **Caps.** Two hours of wall clock on one issue → **Blocked**, regardless of
  progress.

Never bypass the commit hook, never merge on a red or pending check, and never
weaken a test to get green.

## Blocked

Stop only for: credentials needed, an irreversible or cost-incurring action,
retry caps exhausted, or an empty backlog. Then:

1. Comment the question and the options on the issue.
2. Set the board item back to **In progress** (`9bf45f6f`).
3. Add the `status: blocked` label.
4. Write the question into the **Current blockers** section of
   `docs/project-status.md`, so a fresh session recovers it via the startup
   protocol. Land it by the same route as the status update in §8 — a
   `docs/<short-name>` branch and a pull request through §5 to §7, under the
   same exemption. There is no direct push to `main` here either.
5. Report to the user.
