"""Read AndroidJUnitRunner's -w -r protocol, preserving failures as JUnit XML."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def write_junit(transcript: str, output: Path) -> int:
    bundle: dict[str, str] = {}
    cases: list[tuple[str, str, int, str]] = []
    expected = set()
    started = set()
    problem = []
    last_key = None
    for line in transcript.splitlines():
        field = re.fullmatch(r"INSTRUMENTATION_STATUS: ([A-Za-z0-9_]+)=(.*)", line)
        code = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        if field:
            last_key = field[1]
            bundle[last_key] = field[2]
        elif code:
            value = int(code[1])
            identity = (bundle.get("class", ""), bundle.get("test", ""))
            if bundle.get("numtests", "").isdigit(): expected.add(int(bundle["numtests"]))
            if not all(identity): problem.append("Test status has no class/name")
            elif value == 1:
                if identity in started: problem.append("Duplicate test start")
                started.add(identity)
            elif value in (0, -1, -2, -3, -4):
                if identity not in started: problem.append("Test finished without starting")
                if any((c, t) == identity for c, t, _, _ in cases): problem.append("Duplicate test completion")
                cases.append((*identity, value, bundle.get("stack", bundle.get("stream", ""))))
            else: problem.append(f"Unknown status code {value}")
            bundle = {}; last_key = None
        elif last_key and not line.startswith("INSTRUMENTATION_"):
            bundle[last_key] += "\n" + line
    passed = sum(code == 0 for _, _, code, _ in cases)
    summary = re.findall(r"^OK \((\d+) tests?\)\s*$", transcript, re.MULTILINE)
    terminal = re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\s*$", transcript, re.MULTILINE)
    if summary != [str(passed)] or terminal != ["-1"] or not passed:
        problem.append("Missing or inconsistent successful runner summary")
    if expected != {len(cases)} or started != {(c, t) for c, t, _, _ in cases}:
        problem.append("Incomplete instrumentation run")
    if any(code != 0 for _, _, code, _ in cases): problem.append("Instrumentation failed or skipped tests")
    suite = ET.Element("testsuite", name="AndroidJUnitRunner", tests=str(len(cases) + bool(problem)),
        failures=str(sum(code == -2 for _, _, code, _ in cases)),
        errors=str(sum(code == -1 for _, _, code, _ in cases) + bool(problem)),
        skipped=str(sum(code in (-3, -4) for _, _, code, _ in cases)))
    for classname, name, code, stack in cases:
        case = ET.SubElement(suite, "testcase", classname=classname, name=name)
        if code in (-1, -2): ET.SubElement(case, "error" if code == -1 else "failure").text = stack
        elif code in (-3, -4): ET.SubElement(case, "skipped").text = stack
    if problem:
        case = ET.SubElement(suite, "testcase", classname="AndroidJUnitRunner", name="complete_run")
        ET.SubElement(case, "error").text = "; ".join(problem)
    output.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suite).write(output, encoding="utf-8", xml_declaration=True)
    if problem: raise RuntimeError("; ".join(problem))
    return passed
