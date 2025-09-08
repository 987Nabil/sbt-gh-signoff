package signoff

import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.charset.StandardCharsets
import scala.util.Try

private[signoff] object Report {

  final case class Entry(stage: String, success: Boolean, message: String)

  def clearIfNeeded(file: Path, clear: Boolean): Unit = {
    if (clear && Files.exists(file)) Files.delete(file)
  }

  def append(file: Path, entry: Entry, includeSuccess: Boolean): Unit = {
    if (!entry.success && entry.message.trim.isEmpty) return
    if (entry.success && !includeSuccess) return
    ensureParent(file)
    val line =
      if (entry.success) s"[ OK ] ${entry.stage} :: ${entry.message}"
      else s"[FAIL] ${entry.stage} :: ${entry.message}"
    Files.write(
      file,
      (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE, StandardOpenOption.APPEND
    )
  }

  def summary(file: Path): String =
    if (Files.exists(file))
      new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
    else "(no report)"

  private def ensureParent(file: Path): Unit =
    Option(file.getParent).foreach(p => if (!Files.exists(p)) Files.createDirectories(p))

  def hasChanges(previousSnapshot: Option[String], file: Path): Boolean = {
    val now = if (Files.exists(file)) Some(summary(file)) else None
    now != previousSnapshot
  }

  def snapshot(file: Path): Option[String] =
    if (Files.exists(file)) Some(summary(file)) else None

  def stageHeader(file: Path, title: String): Unit = {
    ensureParent(file)
    if (Files.exists(file)) {
      val sep = s"== $title =="
      Files.write(
        file,
        (sep + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.APPEND
      )
    }
  }

  def writeFailureBlock(file: Path, failures: List[Entry]): Unit = {
    if (failures.nonEmpty) {
      stageHeader(file, "Quality Failures")
      failures.foreach(f => append(file, f, includeSuccess = true))
      Files.write(
        file,
        s"SUMMARY: quality stage completed with ${failures.size} failure(s).${System.lineSeparator()}".getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.APPEND
      )
    }
  }
}
