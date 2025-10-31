ThisBuild / scalaVersion := "3.7.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "api",
    version := "0.1.0-SNAPSHOT",

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )

lazy val domain = project
  .in(file("modules/domain"))
  .settings(
    name := "domain"
  )
