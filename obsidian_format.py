#!/usr/bin/env python3
"""Convert repo .md files to Obsidian-compatible format with YAML frontmatter and flashcards."""

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))

SKIP_FILES = {"README.md", "CLAUDE.md"}
SKIP_DIRS = {"_Archive", "agents", "00_Repository_Maintenance"}

stats = {"updated": 0, "unchanged": 0, "skipped": 0, "errors": 0}


def should_skip(path: str) -> bool:
    rel = os.path.relpath(path, REPO_ROOT)
    parts = rel.split(os.sep)
    if parts[0] in SKIP_DIRS:
        return True
    for part in parts:
        if part in SKIP_DIRS:
            return True
    if parts[-1] in SKIP_FILES:
        return True
    return False


def to_title_case(s: str) -> str:
    # Strip leading digits, underscores, spaces, dots
    s = re.sub(r'^[\d_\.\s-]+', '', s)
    return s.replace('-', ' ').replace('_', ' ').title()


def to_kebab(s: str, max_len: int = 30) -> str:
    s = re.sub(r'^[\d_\.\s-]+', '', s)
    s = s.lower().replace(' ', '-').replace('_', '-')
    s = re.sub(r'[^a-z0-9\-]', '', s)
    s = re.sub(r'-+', '-', s).strip('-')
    return s[:max_len].rstrip('-')


def build_frontmatter(path: str) -> str:
    rel = os.path.relpath(path, REPO_ROOT)
    parts = rel.split(os.sep)
    # parts[0] = top-level folder (or filename if at root)
    # parts[1] = second-level folder or filename
    # parts[2] = third-level folder or filename

    if len(parts) == 1:
        # Root-level file — no folder hierarchy
        module = "root"
        topic = ""
        subtopic = ""
        topic_source = parts[0].replace('.md', '')
    elif len(parts) == 2:
        module = parts[0]
        topic = ""
        subtopic = ""
        topic_source = parts[0]
    elif len(parts) == 3:
        module = parts[0]
        topic = to_title_case(parts[1])
        subtopic = ""
        topic_source = parts[1]
    else:
        module = parts[0]
        topic = to_title_case(parts[1])
        subtopic = to_title_case(parts[2])
        topic_source = parts[1]

    module_lower = module.lower()
    topic_kebab = to_kebab(topic_source)

    tags = [t for t in [module_lower, "system-design", topic_kebab] if t]
    # Deduplicate while preserving order
    seen = set()
    tags = [x for x in tags if not (x in seen or seen.add(x))]
    tags_str = "[" + ", ".join(tags) + "]"

    lines = ["---", f"module: {module}"]
    if topic:
        lines.append(f"topic: {topic}")
    if subtopic:
        lines.append(f"subtopic: {subtopic}")
    lines += [
        "status: unread",
        f"tags: {tags_str}",
        "---",
    ]
    return "\n".join(lines) + "\n"


def strip_existing_header(content: str) -> str:
    """Remove leading plain-text header blocks and/or YAML frontmatter."""
    # Strip YAML frontmatter (--- ... ---)
    if content.startswith('---'):
        end = content.find('\n---', 3)
        if end != -1:
            content = content[end + 4:].lstrip('\n')

    # Strip plain-text header block: lines like MODULE:, TOPIC:, PST: above a --- delimiter
    header_pattern = re.compile(
        r'^(?:(?:[A-Z][A-Z0-9_ ]+:.*\n)+---+\n)',
        re.MULTILINE
    )
    content = header_pattern.sub('', content, count=1)

    return content


def extract_bullets_from_quick_recall(content: str):
    """Find ## Quick Recall section and extract bullet lines from it."""
    # Find the Quick Recall section
    match = re.search(r'^##\s+Quick Recall\s*$', content, re.MULTILINE)
    if not match:
        return []

    start = match.end()
    # Find next ## heading or end of file
    next_heading = re.search(r'^##\s+', content[start:], re.MULTILINE)
    if next_heading:
        section = content[start: start + next_heading.start()]
    else:
        section = content[start:]

    bullets = []
    for line in section.split('\n'):
        stripped = line.strip()
        if stripped.startswith('- ') or stripped.startswith('* '):
            text = stripped[2:].strip()
            if text:
                bullets.append(text)
    return bullets


def make_flashcards(bullets: list) -> str:
    if not bullets:
        return ""
    lines = ["", "## Flashcards", ""]
    for bullet in bullets:
        # Split on " — " or ": " (first occurrence only)
        split_match = re.search(r' — |: ', bullet)
        if split_match:
            left = bullet[:split_match.start()].strip()
            right = bullet[split_match.end():].strip()
            question = left
            answer = right
        else:
            question = bullet
            answer = bullet
        lines.append(f"**{question}**? #flashcard")
        lines.append(answer)
        lines.append("")
    return "\n".join(lines)


def already_has_flashcards(content: str) -> bool:
    return bool(re.search(r'^## Flashcards\s*$', content, re.MULTILINE))


def process_file(path: str):
    try:
        with open(path, 'r', encoding='utf-8') as f:
            original = f.read()

        # Strip existing frontmatter/header
        body = strip_existing_header(original)

        # Build new frontmatter
        fm = build_frontmatter(path)

        # Extract bullets from Quick Recall section for flashcards
        bullets = extract_bullets_from_quick_recall(body)

        # Build flashcards block
        flashcards = make_flashcards(bullets)

        # Remove existing ## Flashcards section if present (we'll re-append)
        body_no_fc = re.sub(
            r'\n## Flashcards\s*\n.*',
            '',
            body,
            flags=re.DOTALL
        )

        # Compose final content
        new_content = fm + body_no_fc.lstrip('\n')
        if flashcards:
            new_content = new_content.rstrip('\n') + '\n' + flashcards

        if new_content == original:
            stats["unchanged"] += 1
            return

        with open(path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        stats["updated"] += 1
        print(f"  [updated] {os.path.relpath(path, REPO_ROOT)}")

    except Exception as e:
        stats["errors"] += 1
        print(f"  [ERROR] {os.path.relpath(path, REPO_ROOT)}: {e}", file=sys.stderr)


def collect_files():
    result = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        # Prune skip dirs in-place
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith('.')]
        for fname in filenames:
            if not fname.endswith('.md'):
                continue
            full = os.path.join(dirpath, fname)
            if should_skip(full):
                stats["skipped"] += 1
                continue
            result.append(full)
    return result


def main():
    files = collect_files()
    print(f"Processing {len(files)} files...\n")
    for f in sorted(files):
        process_file(f)
    print(f"\n--- Summary ---")
    print(f"  updated:   {stats['updated']}")
    print(f"  unchanged: {stats['unchanged']}")
    print(f"  skipped:   {stats['skipped']}")
    print(f"  errors:    {stats['errors']}")


if __name__ == '__main__':
    main()
