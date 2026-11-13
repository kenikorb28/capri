#!/usr/bin/env python3
"""Sets the ecosystem's one version everywhere it is written.

    python tools/set_version.py 2.0.2

Capri Desktop and Capri Android share one version (the VERSION file). A published Release is found by its number: Capri
Desktop's updater installs a Release only when its number is higher than its own, and Android only installs an APK over
the app when its versionCode is not lower. So every published version needs a new number, the same in all these places.
The Capri Release workflow refuses to publish when they disagree.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
        sys.exit("Usage: python tools/set_version.py 2.0.2   (three numbers)")
    version = sys.argv[1]
    major, minor, patch = (int(part) for part in version.split("."))
    if minor > 99 or patch > 99:
        sys.exit("The middle and last numbers stop at 99: Android's versionCode is major*10000 + minor*100 + patch.")
    code = major * 10000 + minor * 100 + patch

    # (file, pattern with the part before the version in group 1, what replaces the version, how many times)
    edits = [
        ("VERSION", r"\A()\d+\.\d+\.\d+", version, 1),
        ("desktop/app/AssemblyInfo.cs", r'(Assembly(?:File)?Version\(")\d+\.\d+\.\d+', version, 2),
        ("desktop/cross/Capri.Cross.csproj", r"(<Version>)\d+\.\d+\.\d+", version, 1),
        ("android/app/build.gradle.kts", r"(versionCode = )\d+", str(code), 1),
        ("android/app/build.gradle.kts", r'(versionName = ")\d+\.\d+\.\d+', version, 1),
        ("README.md", r"(Version unique de l'écosystème : \*\*)\d+\.\d+\.\d+", version, 1),
    ]
    texts = {}
    for name, pattern, value, expected in edits:
        if name not in texts:
            # newline="" keeps each file's own line endings.
            with open(ROOT / name, encoding="utf-8", newline="") as file:
                texts[name] = file.read()
        texts[name], count = re.subn(pattern, lambda match: match.group(1) + value, texts[name])
        if count != expected:
            sys.exit(f"{name}: expected {expected} place(s) for /{pattern}/, found {count}. Nothing was written.")
    for name, text in texts.items():
        with open(ROOT / name, "w", encoding="utf-8", newline="") as file:
            file.write(text)
    print(f"Capri {version} (Android versionCode {code}) written to: " + ", ".join(texts))


if __name__ == "__main__":
    main()
