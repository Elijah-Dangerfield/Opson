#!/usr/bin/env python3
"""Drive the app on a connected Android device or emulator, by *text*.

Exists so an agent can verify UI work without a human at the screen: launch the
app, read what is on it, tap something by its label, capture a PNG.

    scripts/dev/drive.py launch [--fresh]
    scripts/dev/drive.py text
    scripts/dev/drive.py tap "Continue"
    scripts/dev/drive.py shot /tmp/home.png
    scripts/dev/drive.py --dry-run launch       # print the adb commands only

Tapping by coordinate breaks every time a layout shifts or the app boots a
second slower, so nothing here takes coordinates. It dumps the accessibility
tree, finds the node whose label matches, and taps its centre, retrying until
the node appears.

`launch` uses `am start`, deliberately, and NOT `monkey -p <pkg> -c LAUNCHER 1`.
Monkey is the obvious way to start an app by package name and it is the wrong
one: its trailing number is not a repeat count, it is how many pseudo-random
input events monkey fires *after* the app comes up. Every launch injects one
stray event into the first frame.

Measured downstream, from a clean install: 1 launch in 16 ended up somewhere
other than where it should have been, once leaving the device on the launcher
entirely. During one verification session it closed a launch-gate banner twice
and wrote a persisted dismissal, which made the feature under test look broken.

Roughly 6% is the number that makes this worth a comment rather than a commit
message. Tooling that fails outright gets fixed. Tooling that acts on the app
occasionally makes every screenshot after it one interaction ahead of where you
think you are, and the app takes the blame.

`text` and `content-desc` are the only labels visible here. A `uiautomator` dump
carries no state-description attribute, because `getStateDescription()` is
readable only by an accessibility service. A control that puts its identity in
`contentDescription` and its state in `stateDescription` — the correct split —
reads here as if the state were missing. Identical output from this script is
therefore not evidence that two states are announced the same way, and a bug has
already been filed once against a control that was announcing itself correctly.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree

REPO_ROOT = Path(__file__).resolve().parents[2]
VERSIONS_FILE = REPO_ROOT / "versions.properties"

# The debug build type carries an applicationIdSuffix (see the application
# convention plugin), so the package on the device is not the applicationId in
# versions.properties. Debug is what an agent has just built, so it wins when
# both are installed.
DEBUG_SUFFIX = ".debug"

BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")

# Placeholders used only by --dry-run, where nothing is asked of a device.
UNRESOLVED_ACTIVITY = "<launcher-activity>"


class Adb:
    """One device, one `adb` prefix.

    Under `--dry-run` every invocation is printed and answers with empty
    output, so the polling loops below have to check the flag themselves: with
    no device replying, each would otherwise sit out its full timeout printing
    the same command.
    """

    def __init__(self, serial: str | None, dry_run: bool):
        self.serial = serial
        self.dry_run = dry_run

    def __call__(self, *args: str, binary: bool = False):
        command = ["adb", *(["-s", self.serial] if self.serial else []), *args]
        if self.dry_run:
            print("+ " + " ".join(command))
            return b"" if binary else ""
        result = subprocess.run(command, capture_output=True, check=False)
        return result.stdout if binary else result.stdout.decode(errors="replace")


def application_id() -> str:
    """The applicationId, read the way the release workflow reads it."""
    for line in VERSIONS_FILE.read_text(encoding="utf-8").splitlines():
        if line.startswith("applicationId="):
            value = line.split("=", 1)[1].strip()
            if value:
                return value
    sys.exit(f"error: no applicationId in {VERSIONS_FILE}")


def pick_serial(requested: str | None) -> str | None:
    """Resolve --device, or the only attached device.

    Returned as None when there is exactly one, so the printed commands stay
    the ones a human would type. With several attached, adb itself would fail
    with "more than one device", which says nothing about which to pick.
    """
    if requested:
        return requested
    listing = subprocess.run(["adb", "devices"], capture_output=True, check=False)
    attached = [
        line.split("\t", 1)[0]
        for line in listing.stdout.decode(errors="replace").splitlines()[1:]
        if line.strip().endswith("\tdevice")
    ]
    if not attached:
        sys.exit("error: no device attached. Start an emulator or plug one in.")
    if len(attached) > 1:
        sys.exit(f"error: several devices attached ({', '.join(attached)}). Pass --device.")
    return None


def installed_package(adb: Adb, explicit: str | None) -> str:
    if explicit:
        return explicit
    base = application_id()
    candidates = [base + DEBUG_SUFFIX, base]
    if adb.dry_run:
        return candidates[0]
    for candidate in candidates:
        listed = adb("shell", "pm", "list", "packages", candidate)
        if any(line.strip() == f"package:{candidate}" for line in listed.splitlines()):
            return candidate
    sys.exit(
        f"error: neither {candidates[0]} nor {candidates[1]} is installed. "
        "Build and install first: ./gradlew :apps:compose:installDebug"
    )


def launcher_activity(adb: Adb, package: str) -> str:
    """Ask the device which component the launcher would start.

    Resolved rather than hardcoded or parsed out of a merged manifest: the
    manifest is a build output that may not exist, and the device's answer is
    the one `am start` will act on.
    """
    if adb.dry_run:
        adb("shell", "cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER", package)
        return UNRESOLVED_ACTIVITY
    resolved = adb("shell", "cmd", "package", "resolve-activity", "--brief",
                   "-a", "android.intent.action.MAIN",
                   "-c", "android.intent.category.LAUNCHER", package)
    for line in reversed(resolved.splitlines()):
        line = line.strip()
        if line.startswith(f"{package}/"):
            return line.split("/", 1)[1]
    sys.exit(f"error: {package} has no launcher activity (resolve-activity said: {resolved.strip()})")


def nodes(adb: Adb) -> list[tuple[str, int, int]]:
    """Every labelled node on screen, as (label, centre x, centre y).

    Parsed as XML rather than scanned with a regex, and both halves of that
    matter.

    The scanning version looked for `(text|content-desc)="..."` followed by
    `bounds`, which reads as equivalent and is not. uiautomator emits `text`
    before `content-desc` on every node, so on a node with `text=""` the
    alternation matched the empty text, ran on to that node's `bounds` and
    consumed the element — and `finditer` does not overlap, so the real
    `content-desc` was never seen.

    Nodes labelled *only* by a content description were therefore invisible,
    and it failed in the least helpful way: icon buttons and custom-drawn
    controls are exactly that kind of node, so `tap` reported "never found" for
    a control plainly on screen while anything with visible text worked, which
    reads as a labelling bug in the app.

    Parsing per element fixed that and left a second bug behind. Attribute
    values in the dump are XML-escaped, so a label reading `Cats & dogs` came
    back as `Cats &amp; dogs` and tapping the text actually on screen did not
    match. A real parser decodes entities; a regex hands back the source.

    A node can carry both a `text` and a `content-desc`. Both are yielded, so
    either spelling can be tapped.
    """
    adb("shell", "rm", "-f", "/sdcard/ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    # `exec-out`, not `shell`: `adb shell` translates LF to CRLF, which is
    # enough to make the parser reject the document.
    dump = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True)
    try:
        root = ElementTree.fromstring(dump)
    except ElementTree.ParseError:
        # A dump can be empty or truncated when it lands mid-transition. Every
        # caller polls, so an empty screen is the honest answer and raising
        # here would only turn a retry into a stack trace.
        return []

    found = []
    for node in root.iter("node"):
        bounds = BOUNDS.match(node.get("bounds", ""))
        if not bounds:
            continue
        x1, y1, x2, y2 = (int(value) for value in bounds.groups())
        centre = ((x1 + x2) // 2, (y1 + y2) // 2)
        for attribute in ("text", "content-desc"):
            label = node.get(attribute)
            if label:
                found.append((label, *centre))
    return found


def launch(adb: Adb, package: str, fresh: bool) -> int:
    adb("shell", "am", "force-stop", package)
    if fresh:
        adb("shell", "pm", "clear", package)
    activity = launcher_activity(adb, package)
    # Explicit component, no injected events. The module docstring has the
    # measured cost of the `monkey` spelling; do not reintroduce it.
    adb("shell", "am", "start", "-W",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER",
        "-n", f"{package}/{activity}")

    # Wait for the process to actually be resumed rather than sleeping a fixed
    # amount: a cold start behind a boot gate (remote config, migrations) takes
    # as long as it takes, and a sleep tuned to a warm launch races it.
    deadline = time.time() + 60
    while time.time() < deadline:
        if package in adb("shell", "dumpsys", "activity", "activities") and \
                package in adb("shell", "dumpsys", "window", "windows"):
            break
        if adb.dry_run:
            break
        time.sleep(1)
    # Then wait for the first frame that has anything readable on it, so the
    # next command in the session does not race the splash.
    for _ in range(40):
        if any(label for label, _, _ in nodes(adb)):
            break
        if adb.dry_run:
            break
        time.sleep(1)
    return 0


def tap(adb: Adb, label: str, timeout: float) -> int:
    target = label.casefold()
    deadline = time.time() + timeout
    while True:
        on_screen = nodes(adb)
        for text, x, y in on_screen:
            if target in text.casefold():
                adb("shell", "input", "tap", str(x), str(y))
                print(f"tapped '{text}' at ({x},{y})")
                return 0
        if adb.dry_run:
            # Nothing dumped the tree, so there are no coordinates to print.
            # The shape of the command is the part worth reviewing.
            adb("shell", "input", "tap", "<x>", "<y>")
            return 0
        if time.time() >= deadline:
            print(f"never found '{label}'. on screen: {[t for t, _, _ in on_screen]}", file=sys.stderr)
            return 1
        time.sleep(1)


def shot(adb: Adb, path: str) -> int:
    image = adb("exec-out", "screencap", "-p", binary=True)
    if adb.dry_run:
        return 0
    # Checked rather than written blind. A capture taken through `adb shell`
    # instead of `exec-out` arrives with its LF bytes doubled and a device that
    # is still booting returns nothing at all; both write a file that looks
    # plausible in a listing and opens as garbage several steps later.
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        print(f"error: screencap returned {len(image)} bytes that are not a PNG", file=sys.stderr)
        return 1
    Path(path).write_bytes(image)
    print(f"wrote {path} ({len(image)} bytes)")
    return 0


def main() -> int:
    # Shared options live on the subcommands, not on the top-level parser, so
    # the spelling that works is `drive.py launch --dry-run`. That is the one
    # people reach for; `drive.py --dry-run launch` is rejected outright with
    # "unrecognized arguments".
    #
    # Rejected outright is the requirement, and putting them in *both* places is
    # the trap. A subparser writes into the same namespace after the top-level
    # parser has, so an ordinary `False` default on the subcommand's copy
    # silently overwrites a `--dry-run` passed before the subcommand, and the
    # run touches the device for real while the caller believes it is a
    # rehearsal. `default=argparse.SUPPRESS` does not save it either: with
    # subparsers the top-level value is still lost. Declared once, a
    # misplacement is a parse error, which is the only failure mode worth
    # having on a flag whose whole job is "do not act on the device".
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--device", help="adb serial, when more than one is attached")
    common.add_argument("--package", help="override the resolved package name")
    common.add_argument("--dry-run", action="store_true",
                        help="print the adb commands instead of running them")

    parser = argparse.ArgumentParser(
        prog="drive.py",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    commands = parser.add_subparsers(dest="command", required=True)

    launch_command = commands.add_parser(
        "launch", parents=[common], help="start the app and wait for its first frame")
    launch_command.add_argument("--fresh", action="store_true",
                                help="pm clear first, for a clean-install run")
    tap_command = commands.add_parser(
        "tap", parents=[common], help="tap the node whose label contains TEXT")
    tap_command.add_argument("text")
    tap_command.add_argument("--timeout", type=float, default=30.0,
                             help="seconds to wait for the node to appear (default: 30)")
    shot_command = commands.add_parser(
        "shot", parents=[common], help="save a PNG screenshot")
    shot_command.add_argument("path")
    commands.add_parser("text", parents=[common], help="print every label on screen")

    args = parser.parse_args()
    # A dry run must work with nothing attached, so it takes --device as given.
    serial = args.device if args.dry_run else pick_serial(args.device)
    adb = Adb(serial, args.dry_run)

    if args.command == "text":
        print("\n".join(label for label, _, _ in nodes(adb)))
        return 0
    if args.command == "shot":
        return shot(adb, args.path)

    package = installed_package(adb, args.package)
    if args.command == "launch":
        return launch(adb, package, args.fresh)
    return tap(adb, args.text, args.timeout)


if __name__ == "__main__":
    raise SystemExit(main())
