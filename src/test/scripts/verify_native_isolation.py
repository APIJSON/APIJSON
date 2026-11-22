"""Build independent native references from Git history and check the saved contracts.

Run after `mvn -o test`: python src/test/scripts/verify_native_isolation.py
Use --refresh-baseline only to capture a reviewed, independently built reference.
All temporary sources, classes, logs and comparisons go under target/native-audit.
No checkout, branch, index or remote is modified.
"""
import argparse
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "target/native-audit"
BASELINE = ROOT / "src/test/resources/native-isolated-upstream.tsv"
REVISIONS = {
    "v820": ("d86a6405", "APIJSONORM/"),
    "pre": ("2ea3700e", "APIJSONORM/"),
    "merge": ("8a8d33ee", "APIJSONORM/"),
    "main": ("cdfb8d03", ""),
}


def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, capture_output=True, timeout=90, **kwargs)


def extract(name, revision, prefix):
    result = run(["git", "archive", "--format=zip", revision, prefix + "src/main/java"])
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    with zipfile.ZipFile(io.BytesIO(result.stdout)) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            relative = Path(entry.filename.removeprefix(prefix))
            assert not relative.is_absolute() and ".." not in relative.parts
            dest = OUT / name / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(archive.read(entry))


def build_reference():
    # Remove the complete initial adaptation (pre -> merge), then retain the
    # subsequent upstream edits (merge -> main). Resolve overlaps by purpose.
    conflicts = {}
    for source in (OUT / "main/src/main/java").rglob("*.java"):
        relative = source.relative_to(OUT / "main")
        ours, base = OUT / "pre" / relative, OUT / "merge" / relative
        if not ours.exists():
            continue  # KingbaseSQLDialect was introduced by the adaptation.
        result = run(["git", "merge-file", "-p", str(ours), str(base), str(source)])
        if result.returncode not in (0, 1, 2, 3):
            raise RuntimeError(result.stderr.decode(errors="replace"))
        content = result.stdout.decode("utf-8").replace("\r\n", "\n")
        if result.returncode:
            conflicts[source.name] = result.returncode
            index = 0

            def resolve(match):
                nonlocal index
                index += 1
                if source.name in ("SQLConfig.java", "AbstractSQLExecutor.java") or index == 1:
                    return match[1]  # Keep the original declarations, predicate and JDBC mapping.
                if "&& isAggregateWithoutGroup() == false" in match[2]:
                    return match[2].replace(" || isKingBaseSQLServer()", "")
                assert "return config.gainUpdateSQL();" in match[2]
                return match[2]  # Retain upstream CRUD extraction and UPSERT.

            content = re.sub(
                r"^<<<<<<< [^\n]*\n(.*?)^=======\n(.*?)^>>>>>>> [^\n]*\n",
                resolve, content, flags=re.M | re.S)
        if source.name == "AbstractSQLConfig.java":
            assert content.count("isMySQL() || KingbaseSQLDialect.from(gainSQLDatabase()).supportsDmlLimit()") == 2
            content = content.replace(
                "isMySQL() || KingbaseSQLDialect.from(gainSQLDatabase()).supportsDmlLimit()", "isMySQL()")
            start = content.index("\t\tKingbaseSQLDialect kingbaseDialect", content.index("public String gainSelectSQL()"))
            end = content.index("\n\t\tif (isTest()", start)
            content = content[:start] + (
                '\t\tString explain = isExplain() ? (isSQLServer() ? "SET STATISTICS PROFILE ON  "\n'
                '                : (isOracle() || isDameng() || isKingBase() ? "EXPLAIN PLAN FOR " : "EXPLAIN ")) : "";'
            ) + content[end:]
            content = content.replace("isOracle() || isDameng() || isKingBaseOracle()",
                                      "isOracle() || isDameng() || isKingBase()")
            assert not re.search(r"KingbaseSQLDialect|isKingBase(MySQL|Oracle|SQLServer)|DATABASE_KINGBASE_", content)
        assert not re.search(r"^<<<<<<< |^=======$|^>>>>>>> ", content, re.M)
        dest = OUT / "reference" / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")
    assert conflicts == {"AbstractSQLConfig.java": 3, "AbstractSQLExecutor.java": 1, "SQLConfig.java": 1}, conflicts


def java_run(java, classpath, main, *args):
    result = run([str(java), "-cp", classpath, main, *map(str, args)])
    if result.returncode:
        raise RuntimeError((result.stdout + result.stderr).decode(errors="replace"))
    return result.stdout.decode(errors="replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh-baseline", action="store_true")
    options = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (revision, prefix) in REVISIONS.items():
        extract(name, revision, prefix)
    build_reference()
    java_home = Path(os.environ.get("JAVA_HOME", "")) / "bin"
    javac = java_home / "javac.exe" if (java_home / "javac.exe").exists() else shutil.which("javac")
    java = java_home / "java.exe" if (java_home / "java.exe").exists() else shutil.which("java")
    repository = Path(os.environ.get("MAVEN_REPO", str(Path.home() / ".m2/repository")))
    jars = [repository / "junit/junit/4.13.2/junit-4.13.2.jar",
            repository / "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"]
    assert all(path.is_file() for path in jars), "Run Maven tests first to populate the local test dependencies"
    helpers = [ROOT / "src/test/java/apijson/orm" / name for name in (
        "NativeDatabaseBaselineTest.java", "AbstractSQLConfigJoinCountTest.java", "NativeJdbcContractTest.java",
        "NativeRoutingRegressionTest.java")]
    for name in ("v820", "reference"):
        folder = OUT / name
        classes = folder / "classes"
        classes.mkdir(exist_ok=True)
        sources = list((folder / "src/main/java").rglob("*.java")) + helpers
        if name == "reference":
            sources.append(ROOT / "src/test/java/apijson/orm/UpstreamFeatureRegressionTest.java")
        arguments = folder / "compile.args"
        arguments.write_text("\n".join('"' + p.as_posix() + '"' for p in sources), encoding="utf-8")
        result = run([str(javac), "-encoding", "UTF-8", "-source", "8", "-target", "8",
                      "-cp", os.pathsep.join(map(str, jars)), "-d", str(classes), "@" + str(arguments)])
        (folder / "compile.log").write_bytes(result.stdout + result.stderr)
        assert result.returncode == 0, "Reference compilation failed: " + str(folder / "compile.log")
        classpath = os.pathsep.join(map(str, [classes] + jars))
        java_run(java, classpath, "apijson.orm.NativeDatabaseBaselineTest", OUT / (name + ".tsv"))
        tests = ["apijson.orm.NativeJdbcContractTest", "apijson.orm.NativeRoutingRegressionTest"]
        if name == "reference":
            tests.append("apijson.orm.UpstreamFeatureRegressionTest")
        log = java_run(java, classpath, "org.junit.runner.JUnitCore", *tests)
        (OUT / (name + "-contracts.log")).write_text(log, encoding="utf-8")
        print(name + ": reference compiled; contract tests passed", flush=True)
    classpath = os.pathsep.join(map(str, [ROOT / "target/test-classes", ROOT / "target/classes"] + jars))
    java_run(java, classpath, "apijson.orm.NativeDatabaseBaselineTest", OUT / "current.tsv")
    reference = OUT / "reference.tsv"
    new = reference.read_text(encoding="utf-8").splitlines()
    assert (OUT / "current.tsv").read_text(encoding="utf-8").splitlines() == new, (
        "Current native SQL/parameters/errors differ from reference")
    old = (OUT / "v820.tsv").read_text(encoding="utf-8").splitlines()
    shared = [row for row in new if not row.startswith("TURSO|")]
    assert len(old) == len(shared)
    differences = [{"v820": a, "upstream": b} for a, b in zip(old, shared) if a != b]
    assert all(row["v820"].split("|", 3)[:3] == row["upstream"].split("|", 3)[:3]
               and row["v820"].split("|", 3)[2] == "having-column" for row in differences)
    if options.refresh_baseline:
        BASELINE.write_bytes(reference.read_bytes())
    assert BASELINE.read_text(encoding="utf-8").splitlines() == new, (
        "Saved baseline differs from independently rebuilt reference")
    summary = {"revisions": REVISIONS, "native_snapshot_cases": len(new),
               "current_vs_isolated_upstream_differences": 0,
               "expected_v820_upstream_differences": len(differences),
               "upstream_turso_cases": len(new) - len(shared),
               "upstream_having_commit": "bccbf475", "differences": differences}
    (OUT / "comparison.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Native cases: {len(new)}; current/reference differences: 0; expected upstream HAVING differences: {len(differences)}")


if __name__ == "__main__":
    main()
