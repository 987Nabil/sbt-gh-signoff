package signoff

import sbt._
import sbt.Keys._
import scala.sys.process._
import scala.util.{Try, Success, Failure}
import java.nio.file.Path

object SignoffPlugin extends AutoPlugin {

  object autoImport {
    val signoffContexts                = settingKey[Seq[String]]("Contexts to create ('creates base signoff status).")
    val signoffUseQualityBeforeCreate  = settingKey[Boolean]("Run quality pipeline before creating statuses.")
    val signoffVerbose                 = settingKey[Boolean]("Verbose logging.")
    val signoffFailIfGhMissing         = settingKey[Boolean]("Fail if gh CLI not found.")
    val signoffFailIfJqMissing         = settingKey[Boolean]("Fail if jq not found.")
    val signoffDisallowCi              = settingKey[Boolean]("If true, abort when running in CI.")
    val signoffAggregateReportEnabled  = settingKey[Boolean]("Enable aggregated quality report.")
    val signoffReportIncludeSuccess    = settingKey[Boolean]("Include success entries in report.")
    val signoffReportFile              = settingKey[File]("Report output file.")
    val signoffClearReportOnStart      = settingKey[Boolean]("Clear report at start of pipeline.")
    val signoffAutoCommitReport        = settingKey[Boolean]("Auto commit the report before statuses.")
    val signoffCommitReportMessage     = settingKey[String]("Commit message for auto commit.")
    val signoffCommitReportAmend       = settingKey[Boolean]("Whether to amend previous commit (discouraged).")

    val signoffQuality     = taskKey[Unit]("Run quality pipeline (format/fix/compile).")
    val signoffAndTest     = taskKey[Unit]("Run quality pipeline including Test / compile.")
    val signoffCreate      = taskKey[Unit]("Create signoff commit statuses.")
    val signoffStatus      = taskKey[Unit]("Show signoff statuses for current commit.")
    val signoffInstall     = taskKey[Unit]("Install branch protection to require signoff contexts (best effort).")
    val signoffUninstall   = taskKey[Unit]("Remove branch protection requiring signoff contexts.")
    val signoffCheck       = taskKey[Unit]("Check which signoff contexts are required by protection.")
    val signoffShowReport  = taskKey[Unit]("Print the aggregated report.")
    val signoff            = taskKey[Unit]("Run (optionally) quality, auto-commit report, and create signoff statuses.")
  }

  import autoImport._
  import Report._

  override def trigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    signoffContexts               := Seq("", "scala"),
    signoffUseQualityBeforeCreate := true,
    signoffVerbose                := false,
    signoffFailIfGhMissing        := true,
    signoffFailIfJqMissing        := true,
    signoffDisallowCi             := true,
    signoffAggregateReportEnabled := true,
    signoffReportIncludeSuccess   := false,
    signoffReportFile             := target.value / "signoff" / "signoff-report.txt",
    signoffClearReportOnStart     := true,
    signoffAutoCommitReport       := false,
    signoffCommitReportMessage    := "chore(signoff): add quality report",
    signoffCommitReportAmend      := false,

    signoffQuality := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      val reportEnabled = signoffAggregateReportEnabled.value
      val reportFile = signoffReportFile.value.toPath
      if (reportEnabled && signoffClearReportOnStart.value) Report.clearIfNeeded(reportFile, clear = true)
      val entries = scala.collection.mutable.ListBuffer.empty[Entry]

      def runOpt(label: String)(body: => Unit): Unit = {
        val startMsg = s"Running $label ..."
        log.info(startMsg)
        val outcome = Try(body)
        outcome match {
          case Success(_) =>
            log.info(s"$label OK")
            if (reportEnabled) append(reportFile, Entry(label, success = true, "ok"), signoffReportIncludeSuccess.value)
          case Failure(ex) =>
            log.error(s"$label FAILED: ${ex.getMessage}")
            entries += Entry(label, success = false, ex.getMessage)
            if (reportEnabled) append(reportFile, entries.last, includeSuccess = true)
        }
      }

      runOpt("scalafmt") {
        callIfTaskExists("scalafmtAll", state.value)
      }
      runOpt("scalafix") {
        callIfTaskExists("scalafixAll", state.value)
      }
      runOpt("compile") {
        (Compile / compile).value
      }

      log.info("Quality pipeline finished.")
    },

    signoffAndTest := {
      signoffQuality.value
      val log = streams.value.log
      Try((Test / compile).value) match {
        case Success(_) =>
          log.info("Test compile OK")
          if (signoffAggregateReportEnabled.value)
            append(signoffReportFile.value.toPath, Entry("testCompile", success = true, "ok"), signoffReportIncludeSuccess.value)
        case Failure(ex) =>
          log.error(s"Test compile FAILED: ${ex.getMessage}")
          if (signoffAggregateReportEnabled.value)
            append(signoffReportFile.value.toPath, Entry("testCompile", success = false, ex.getMessage), includeSuccess = true)
          throw ex
      }
    },

    signoffCreate := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      checkTools(signoffFailIfGhMissing.value, signoffFailIfJqMissing.value, log)

      val contexts = signoffContexts.value
      val sha = currentGitSha(baseDirectory.value)
      log.info(s"Creating signoff statuses for $sha :: contexts = ${contexts.mkString(",")}")
      contexts.foreach { ctx =>
        val fullCtx = if (ctx.trim.isEmpty) "signoff" else s"signoff/$ctx"
        val cmd = Seq("gh", "api",
          "-X", "POST",
          s"repos/${gitRemoteOwnerAndRepo(baseDirectory.value)}/statuses/$sha",
          "-f", s"state=success",
          "-f", s"context=$fullCtx",
          "-f", s"description=local signoff")
        runCommand(cmd, log, fail = false)
      }
    },

    signoffStatus := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      checkTools(signoffFailIfGhMissing.value, signoffFailIfJqMissing.value, log)
      val sha = currentGitSha(baseDirectory.value)
      val cmd = Seq("gh", "api", s"repos/${gitRemoteOwnerAndRepo(baseDirectory.value)}/commits/$sha/status")
      runCommand(cmd, log, fail = false)
    },

    signoffInstall := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      log.warn("signoffInstall: Branch protection installation is a placeholder (extend as needed).")
    },

    signoffUninstall := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      log.warn("signoffUninstall: Branch protection removal is a placeholder (extend as needed).")
    },

    signoffCheck := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      log.warn("signoffCheck: Branch protection check is a placeholder (extend as needed).")
    },

    signoffShowReport := {
      val f = signoffReportFile.value.toPath
      val log = streams.value.log
      if (!java.nio.file.Files.exists(f)) log.info("(no report)")
      else log.info(System.lineSeparator() + Report.summary(f))
    },

    signoff := {
      guardCi(signoffDisallowCi.value, streams.value.log)
      val log = streams.value.log
      if (signoffUseQualityBeforeCreate.value) {
        log.info("Running quality pipeline before creating statuses...")
        signoffQuality.value
      }

      if (signoffAutoCommitReport.value && signoffAggregateReportEnabled.value) {
        val file = signoffReportFile.value
        if (file.exists()) {
          val amend = signoffCommitReportAmend.value
          val msg = signoffCommitReportMessage.value
          val addCmd = Seq("git", "add", file.getPath)
          runCommand(addCmd, log, fail = false)
          val commitCmd = if (amend) Seq("git", "commit", "--amend", "--no-edit") else Seq("git", "commit", "-m", msg)
          runCommand(commitCmd, log, fail = false)
        } else {
          log.info("No report file to commit.")
        }
      }

      signoffCreate.value
    }
  )

  private def guardCi(disallow: Boolean, log: Logger): Unit = {
    val isCi = sys.env.get("CI").contains("true") || sys.env.contains("GITHUB_ACTIONS")
    if (disallow && isCi)
      sys.error("sbt-signoff: Refusing to run in CI (signoffDisallowCi = true).")
    if (isCi && !disallow)
      log.warn("Running in CI despite guard disabled (signoffDisallowCi=false).")
  }

  private def checkTools(failGh: Boolean, failJq: Boolean, log: Logger): Unit = {
    def exists(bin: String): Boolean =
      Try(Seq("which", bin).!!.trim.nonEmpty).getOrElse(false)

    if (!exists("gh")) {
      val msg = "gh CLI not found."
      if (failGh) sys.error(msg) else log.warn(msg + " (continuing because signoffFailIfGhMissing=false)")
    }
    if (!exists("jq")) {
      val msg = "jq not found."
      if (failJq) sys.error(msg) else log.warn(msg + " (continuing because signoffFailIfJqMissing=false)")
    }
  }

  private def runCommand(cmd: Seq[String], log: Logger, fail: Boolean): Int = {
    log.info(s"[cmd] ${cmd.mkString(" ")}")
    val exit = Try(Process(cmd).!(ProcessLogger(o => log.info(o), e => log.error(e)))).getOrElse(-1)
    if (exit != 0 && fail) sys.error(s"Command failed: ${cmd.mkString(" ")}")
    exit
  }

  private def currentGitSha(baseDir: File): String =
    Try(Process(Seq("git", "rev-parse", "HEAD"), baseDir).!!.trim).getOrElse("UNKNOWN")

  private def gitRemoteOwnerAndRepo(baseDir: File): String = {
    val remote = Try(Process(Seq("git", "config", "--get", "remote.origin.url"), baseDir).!!.trim).getOrElse("unknown/unknown")
    val cleaned =
      if (remote.startsWith("git@")) remote.split(":", 2).lift(1).getOrElse(remote)
      else if (remote.startsWith("https://")) remote.stripPrefix("https://").split("/", 2).lift(1).getOrElse(remote)
      else remote
    cleaned.stripSuffix(".git")
  }

  private def callIfTaskExists(taskName: String, state: State): Unit = {
    val cmd = s"$taskName"
    // fire and forget; if it fails it will just log
    Command.process(cmd, state)
    ()
  }
}
