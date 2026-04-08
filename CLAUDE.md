# Project Guidelines

## Commit & PR Rules

- Do NOT include `Co-Authored-By` lines referencing Claude in commit messages.
- Do NOT include "Generated with Claude Code" or similar AI attribution in PR descriptions.

## Code Style

- Do NOT use fully-qualified names (FQN) inline in Kotlin code. Always add a proper `import` statement at the top of the file and reference the type by its simple name. This applies to both production and test code.

## Skills

- Code review follow-up: use the `/resolve-coderabbit-review` skill to triage and apply CodeRabbit comments on the current PR.
- Release: use the `/release` skill to publish to Maven Central and draft GitHub release notes.