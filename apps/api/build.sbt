// morecat API — JVM 単一 sbt ビルド（ビルドルート = apps/api）。
// ヘクサゴナル: domain(純粋) <- application <- infrastructure <- bootstrap。

val scala3      = "3.8.4"
val zioVersion  = "2.1.26"
val ironVersion = "3.3.1"
val zioJsonVersion = "0.9.0"

ThisBuild / scalaVersion := scala3
ThisBuild / organization := "morecat"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
)

ThisBuild / coverageEnabled := false
ThisBuild / coverageMinimumStmtTotal := 100
ThisBuild / coverageMinimumBranchTotal := 100
ThisBuild / coverageFailOnMinimum := true

lazy val root = project
  .in(file("."))
  .aggregate(domain, application, infrastructure)
  .settings(
    name := "morecat-api",
    publish / skip := true,
  )

// 純粋ドメイン: Article イベント ADT・値オブジェクト(Iron)・projection fold。
// IO や wire フォーマット(JSON 等)への依存を載せないことをビルドで強制する。
// JSON codec は infrastructure 層の関心事。RMU(Rust) とはコードを共有せず、
// イベント wire スキーマを契約フィクスチャで整合させる。
lazy val domain = project
  .in(file("domain"))
  .settings(
    name := "morecat-domain",
    libraryDependencies ++= Seq(
      "io.github.iltotore" %% "iron"         % ironVersion,
      "dev.zio"            %% "zio-test"     % zioVersion % Test,
      "dev.zio"            %% "zio-test-sbt" % zioVersion % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )

lazy val application = project
  .in(file("application"))
  .dependsOn(domain)
  .settings(
    name := "morecat-application",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )

lazy val infrastructure = project
  .in(file("infrastructure"))
  .dependsOn(application)
  .settings(
    name := "morecat-infrastructure",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-json"     % zioJsonVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )
