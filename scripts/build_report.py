"""Print local verification results in CI; never reads user/device data."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

reports = sorted(Path("app/build/test-results").glob("**/TEST-*.xml"))
totals = dict(tests=0, failures=0, errors=0, skipped=0)
for path in reports:
    suite = ET.parse(path).getroot()
    for name in totals:
        totals[name] += int(suite.get(name, "0"))
    for case in suite.findall("testcase"):
        for issue in [*case.findall("failure"), *case.findall("error")]:
            print(f"FAILED {case.get('classname')}.{case.get('name')}: {issue.get('message')}")
if reports:
    print("VERIFICATION_TOTALS " + json.dumps(totals, sort_keys=True))
for path in Path("app/schemas").glob("**/*.json"):
    print("ROOM_SCHEMA_PATH " + str(path))
    print(path.read_text())
