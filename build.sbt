ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / licenses     := Seq("The Unlicense" -> url("https://unlicense.org"))
ThisBuild / homepage     := Some(url("https://github.com/987Nabil/sbt-gh-signoff"))

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-signoff",
    description := "Local-only signoff status + quality aggregator plugin",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    libraryDependencies ++= Nil,
  )
