import sbt.*
import Keys.*

ThisBuild / organization := "io.github.987nabil"
ThisBuild / organizationName := "Nabil Abdel-Hafeez"
ThisBuild / homepage := Some(url("https://github.com/987Nabil/sbt-signoff"))

// sbt 1.x plugins must use Scala 2.12 (match the sbt launcher Scala version)
ThisBuild / scalaVersion := "2.12.19"

ThisBuild / licenses := Seq("Unlicense" -> url("https://unlicense.org/"))

ThisBuild / developers += Developer(
  id = "987Nabil",
  name = "Nabil Abdel-Hafeez",
  email = "987.nabil@gmail.com",
  url = url("https://github.com/987Nabil")
  )

// Publishing configuration for sbt-ci-release
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

lazy val root = (project in file("."))
  .enablePlugins(sbt.plugins.JvmPlugin, ScriptedPlugin, CiReleasePlugin)
  .settings(
    name := "sbt-signoff",
    sbtPlugin := true,
    description := "sbt plugin to auto-run scalafmt/scalafix/wartremover and apply GitHub signoff statuses",
    )

Global / onChangedBuildSource := ReloadOnSourceChanges
