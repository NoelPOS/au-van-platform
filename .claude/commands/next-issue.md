---
description: Run one full AU-Van delivery cycle — plan, implement, review, merge, and update the board for the next ready issue.
---

Run one complete delivery cycle for the next ready AU-Van issue. Work one issue
at a time; do not start a second while a pull request is open.

## 1. Sync and select

```sh
git checkout main && git pull --ff-only
gh issue list --state open --json number,title,labels,milestone
gh pr list --state open
gh project item-list 1 --owner NoelPOS --format json --limit 50
```

Take the next issue from the ordered backlog in `docs/project-status.md`. If a
pull request is already open, finish that one instead of starting new work.

## 2. Plan

Set the board item to **In progress** (`9bf45f6f`). Run the `issue-planner`
agent.

- `VERDICT: CREDENTIALS-REQUIRED` → go to **Blocked**.
- `VERDICT: ADR-REQUIRED` → keep going; the drafted ADR gets committed.
- `VERDICT: CLEAR` → keep going.

Post the plan: `gh issue comment <n> --body-file <plan>`.

## 3. Implement

```sh
git checkout -b <type>/<kebab-description>
```

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

"Web checks", "API checks", and "GitGuardian Security Checks" must all be
`SUCCESS`. Red → **Failure handling**.

## 6. Review gate

Run the `pr-reviewer` agent. Then confirm it left nothing behind:

```sh
git status --porcelain
```

Non-empty means the reviewer mutated the tree — abort the cycle and report it.
`VERDICT: REQUEST_CHANGES` → **Failure handling**.

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

## Failure handling

Counters are per issue and reset on merge.

- **CI red.** Infrastructure flake → `gh run rerun --failed` once, not counted.
  Real failure → the implementer fixes it on the same branch with a new
  subject-only commit. **Limit 3.**
- **Review rejected.** The implementer addresses every finding; the reviewer
  runs again from scratch. **Limit 2 rounds.** A third rejection means the issue
  is mis-scoped → **Blocked**.
- **Conflict with `main`.** `git fetch origin && git rebase origin/main`,
  resolve, `git push --force-with-lease`, then re-run **both** CI and the
  reviewer. A conflict in booking or payment logic → **Blocked** immediately;
  never guess at merge semantics for seats or money.
- **`main` goes red after a merge.** Highest priority. Open a `Bug:` issue and
  fix forward; if that does not work in one cycle, revert the merge.
- **Caps.** Six corrective iterations, or two hours on one issue → **Blocked**.

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
