---
description: Run one full AU-Van delivery cycle — plan, implement, review, merge, and update the board for the next ready issue.
---

Run one complete delivery cycle for the next ready AU-Van issue.

## Concurrent tracks

Several issues may run at once when their file surfaces are disjoint. Check
before starting: two tracks must not both touch the same package, the same
migration sequence, or the same workflow file. Typical safe pairings are an API
issue with an infrastructure issue, or an API issue with a web issue whose
endpoints already exist on `main`.

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
cd .worktrees/<short-name>
```

`.worktrees/` sits inside the repository, so the agent file tools can write
there, and it is git-ignored. `git worktree` is not in the
`.claude/settings.json` allow list, so the command prompts for approval each
time; adding the entry is the repository owner's call and is tracked in #36.

Work there for the whole cycle, and remove it with `git worktree remove` once
the pull request has merged.

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

Expect one such comment per review round. Zero means the reviewer did not post
— abort the cycle and report it. Reviews that live only in an agent transcript
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
   protocol.
5. Report to the user.
