# Repo tooling

Maintenance scripts that keep the repo's internal links from rotting as files are renamed or moved.

| Script | What it does |
|--------|--------------|
| [`check-links.sh`](check-links.sh) | Scans every `*.md` file and reports any relative link whose target file is missing. Exits non-zero if any are broken. Run with no args to scan the whole repo, or pass specific files. |
| [`pre-commit`](pre-commit) | Git hook that runs `check-links.sh` against staged `*.md` files and blocks the commit if a link is broken. Bypass with `git commit --no-verify`. |
| [`install-hooks.sh`](install-hooks.sh) | Symlinks `pre-commit` into `.git/hooks/`. Run once per clone (git doesn't version the hooks dir). |

## Setup (once per clone)

```bash
./scripts/install-hooks.sh
```

## Ad-hoc check

```bash
./scripts/check-links.sh
```
