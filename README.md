# sbt-signoff (Local Use Only)

An sbt plugin that:

- Automatically detects and runs **Scalafmt**, **Scalafix**, and **WartRemover** (if present).
- Lets you specify which subprojects to process (or defaults to the current project).
- Provides `signoff` tasks that create **GitHub commit statuses** (`signoff` and `signoff/<context>`) using the GitHub CLI (`gh`).
- Uses external `jq` for JSON parsing when reporting status and branch protection.
- Aggregates failures (and optionally successes) into a local report file.
- Can auto-commit that report before creating signoff statuses.

> LOCAL ONLY: The plugin intentionally fails if it detects a CI environment (`CI=true` or `GITHUB_ACTIONS` set) unless you explicitly disable the guard.

## Key Tasks

| Task | Description |
|------|-------------|
| `signoff` | (Optionally) run quality tasks, (optionally) auto-commit report, then create signoff statuses. |
| `signoffQuality` | Run only the quality (format/fix/compile) pipeline. |
| `signoffAndTest` | Run quality pipeline including `Test / compile`. |
| `signoffCreate` | Create signoff statuses for current HEAD. |
| `signoffStatus` | Show signoff status for current commit. |
| `signoffInstall` | Install branch protection requiring signoff contexts. |
| `signoffUninstall` | Remove branch protection for the branch. |
| `signoffCheck` | Check which signoff contexts are required by protection. |
| `signoffShowReport` | Print the aggregated report (if any). |

## Installation (Local)

Publish locally:

```bash
sbt publishLocal
```

In a consuming build’s `project/plugins.sbt`:

```scala
addSbtPlugin("com.example" % "sbt-signoff" % "0.1.0")
```

## Basic Configuration

```scala
import signoff.SignoffPlugin.autoImport._

signoffContexts := Seq("", "scala")       // base context + signoff/scala
signoffUseQualityBeforeCreate := true
signoffVerbose := true

// Fail if gh or jq missing (adjust if you want skipping instead)
signoffFailIfGhMissing := true
signoffFailIfJqMissing := true
```

Run (locally):

```bash
sbt signoff
sbt signoffStatus
```

## Local-Only Guard

By default:

```scala
signoffDisallowCi := true
```

If you really need to bypass (not recommended):

```scala
signoffDisallowCi := false
```

## Aggregated Report

Defaults:

```scala
signoffAggregateReportEnabled := true
signoffReportIncludeSuccess   := false
signoffReportFile             := target.value / "signoff" / "signoff-report.txt"
signoffClearReportOnStart     := true
```

Sample (failures only):

```
== Quality Failures ==
[FAIL] core :: compile :: Cannot resolve symbol FooBar
SUMMARY: quality stage completed with 1 failure(s).
```

Include successes:

```scala
signoffReportIncludeSuccess := true
```

## Auto-Commit Report

Enable committing the quality report BEFORE statuses (so statuses reference the commit containing the report):

```scala
signoffAutoCommitReport := true
signoffCommitReportMessage := "chore(signoff): add quality report"
signoffCommitReportAmend := false // strongly recommended to keep false
```

If the report file hasn’t changed, no commit is created.

## Handling Missing gh or jq

Downgrade hard failures to warnings:

```scala
signoffFailIfGhMissing := false
signoffFailIfJqMissing := false
```

## Notes

- Report only contains quality results when auto-committed (status outcomes happen after the commit).
- Amending (`signoffCommitReportAmend := true`) after statuses would desynchronize statuses from the commit hash—avoid it.

## Origins & Acknowledgments

Inspired by the Basecamp / DHH signoff concept:

- Original Bash impl: https://github.com/basecamp/gh-signoff/
- Rationale (DHH gist): https://gist.github.com/dhh/c5051aae633ff91bc4ce30528e4f0b60

This Scala adaptation is independently maintained; any mistakes are mine.

## License

Released under [The Unlicense](LICENSE).