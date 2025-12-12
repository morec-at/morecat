name := "morecat-api"

ThisBuild / scalaVersion := "3.7.3"
ThisBuild / scalacOptions ++= Seq(
  // format: off
  "-encoding", "utf8",
  // format: on
  "-deprecation",
  "-unchecked",
  "-feature",
  "-Xfatal-warnings",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:params",
  "-Wunused:privates",
  "-Wunused:patvars",
  "-Wunused:implicits"
)

lazy val base = file("modules")

lazy val root = Project(id = "root", base = base)
  .aggregate(domain)

lazy val domain = Project(id = "domain", base = base / "domain")
  .settings(
    name := "domain",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test" % "2.1.22" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.22" % Test,
      "dev.zio" %% "zio-test-magnolia" % "2.1.22" % Test,
      "io.github.iltotore" %% "iron" % "3.2.0"
    )
  )
