#!/usr/bin/env python3
"""Make Sisah2/NG-GL4ES Openmw3 compile with Android NDK r21e libc++.

Openmw3 uses std::regex::multiline in glsl_for_es.cpp.  The libc++ shipped in
NDK r21e does not expose that basic_regex member, even though the project asks
for C++17.  Preserve the same line-oriented match semantics without relying on
the multiline syntax flag.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: fix-openmw3-ndk-r21-regex.py <NG-GL4ES source dir>")

source_dir = Path(sys.argv[1])
path = source_dir / "src/gl/glsl/glsl_for_es.cpp"
text = path.read_text(encoding="utf-8")

old = '''    const std::regex uniformRegex(\n        R"(^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+\\w+(?:\\s*\\[\\s*\\d+\\s*\\])?\\s+\\w+(?:\\s*\\[\\s*\\d+\\s*\\])?\\s*;.*$)",\n        std::regex::ECMAScript | std::regex::multiline);'''
new = '''    // Android NDK r21e libc++ does not provide std::regex::multiline.\n    // Match a complete uniform declaration line explicitly instead.\n    const std::regex uniformRegex(\n        R"((?:^|\\n)[ \\t]*(?:layout[ \\t]*\\([^\\r\\n)]*\\)[ \\t]*)?uniform[ \\t]+\\w+(?:[ \\t]*\\[[ \\t]*\\d+[ \\t]*\\])?[ \\t]+\\w+(?:[ \\t]*\\[[ \\t]*\\d+[ \\t]*\\])?[ \\t]*;[^\\r\\n]*)",\n        std::regex::ECMAScript);'''

if "std::regex::multiline" not in text:
    print("NG-GL4ES regex compatibility: upstream is already compatible; no patch needed")
    raise SystemExit(0)

if old not in text:
    raise SystemExit(
        "NG-GL4ES regex compatibility: std::regex::multiline is present, "
        "but the expected Openmw3 block changed; refusing a blind patch"
    )

text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("NG-GL4ES regex compatibility: replaced std::regex::multiline for NDK r21e")
