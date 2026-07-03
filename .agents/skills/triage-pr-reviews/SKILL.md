---
name: triage-pr-reviews
description: Triage automated GitHub PR review feedback from codex-review, chatgpt-codex-connector, or other bots against the current HEAD. Use after creating or updating a PR, when bot reviews may be asynchronous or stale, and before applying fixes, pushing follow-up commits, or replying to review threads.
---

# Triage PR Reviews

## Overview

Evaluate bot review feedback on a PR before changing code or replying. The goal is to compare every bot comment with the current PR HEAD, classify it, and stop after presenting the triage unless the user has explicitly approved follow-up actions.

## Resolve The PR

- If the user provides a PR number or URL, use it.
- Otherwise resolve the current branch's PR.
- Prefer the GitHub app for PR metadata and comments when available.
- Use `gh` for missing thread state or Actions details. If `gh` is unauthenticated, report that blocker instead of guessing.
- If no PR exists, report that and stop.

## Wait For Bot Reviews

Bot reviews can lag behind PR creation and pushes. Confirm whether the current `head_sha` has bot review output before triage.

Check these sources:

- codex-review sticky issue comment containing `<!-- codex-review -->`
- pull request reviews from bots such as `chatgpt-codex-connector`
- inline review comments

Use each comment's `commit_id`, reviewed commit, or sticky-comment commit marker to determine whether it targets the current HEAD. If HEAD-targeted output has not arrived, poll with a bounded wait such as 120 seconds between checks and a maximum of about 15 minutes. Do not wait indefinitely.

## Classify Feedback

For each actionable bot comment, inspect the current HEAD before deciding. Bot comments often target older commits, moved files, or code that has already changed.

Classify each item as:

- **要対応**: The issue still applies to current HEAD and should be fixed.
- **対応不要（設計意図）**: The current behavior is intentional and matches project design. Cite `docs/design.md`, `docs/slice-1-plan.md`, or relevant local instructions.
- **対応不要（陳腐化）**: The issue was tied to an older commit or has already been fixed. Name the current evidence or fixing commit.
- **曖昧**: The comment needs user input or a design decision.

Skip threads where Codex already replied in the same thread unless the user asks to revisit them.

## Safety Checks

- Run `git status --short` before local edits and do not overwrite unrelated user changes.
- Use `rg`, file reads, PR patches, and `git show <head_sha> --stat` as needed to connect comments to current code.
- If a comment concerns Codex behavior, verify against the Codex manual or official OpenAI docs before deciding.
- Do not reply on GitHub, resolve threads, push fixes, or change code after triage unless the user has approved those actions.

## Output

Present a concise triage summary with one row or bullet per issue:

- classification
- evidence from current HEAD
- proposed action for **要対応** items

If the user approves action, implement the selected fixes, run relevant checks, commit and push, then reply to the relevant review thread with what changed.
