"""Checks the release-please setup is consistent. Run: uv run --with pyyaml python scripts/check-release-setup.py"""
import json, pathlib, sys, yaml

root = pathlib.Path(__file__).resolve().parent.parent
errors = []

def check(cond, msg):
    if not cond:
        errors.append(msg)

cfg = json.loads((root / "release-please-config.json").read_text())
pkg = cfg["packages"]["."]
check(pkg["release-type"] == "simple", "release-type must be simple")
check(pkg["include-component-in-tag"] is False, "tags must be vX.Y.Z")
check("README.md" in [e if isinstance(e, str) else e["path"] for e in pkg["extra-files"]], "README.md must be in extra-files")

manifest = json.loads((root / ".release-please-manifest.json").read_text())
check(manifest == {".": "0.1.18"}, "manifest must be seeded with 0.1.18")

check("x-release-please-version" in (root / "README.md").read_text(), "README needs version marker")

wf = root / ".github/workflows"
ci = yaml.safe_load((wf / "ci.yml").read_text())
check("publish" not in ci["jobs"], "ci.yml must not publish")
check("release" not in ci[True], "ci.yml must not trigger on release")

rp = yaml.safe_load((wf / "release-please.yml").read_text())
check(set(rp["jobs"]) == {"release-please", "publish"}, "release-please.yml jobs")
check(rp["jobs"]["publish"]["needs"] == "release-please", "publish needs release-please")
check("release_created" in rp["jobs"]["publish"]["if"], "publish gated on release_created")
check("sbt ci-release" in json.dumps(rp), "publish runs sbt ci-release")

pt = yaml.safe_load((wf / "pr-title.yml").read_text())
check("amannn/action-semantic-pull-request" in json.dumps(pt), "pr-title uses semantic PR action")

if errors:
    print("\n".join("FAIL: " + e for e in errors)); sys.exit(1)
print("OK")
