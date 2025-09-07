import Dependencies._

ThisBuild / organization := "morecat"
ThisBuild / version := "0.0.1"
ThisBuild / scalaVersion := "3.7.2"

def settingsApp = Seq(
  name := "morecat-api",
  Compile / run / mainClass := Option("morecat.api.MainApp"),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  libraryDependencies ++= Seq(
    zioHttp,
    zioTest,
    zioTestSBT,
    zioTestMagnolia
  )
)

def settingsDocker = Seq(
  Docker / version := version.value,
  dockerBaseImage := "eclipse-temurin:21.0.8_9-jre"
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(settingsApp)
  .settings(settingsDocker)
