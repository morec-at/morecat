// morecat API — JVM 単一 sbt ビルド（ビルドルート = apps/api）。
// ヘクサゴナル: domain(純粋) <- application <- infrastructure <- bootstrap（後者3つはタスク2以降）。

val scala3      = "3.8.4"
val zioVersion  = "2.1.26"
val zioJson     = "0.9.2"
val ironVersion = "3.3.1"

ThisBuild / scalaVersion := scala3
ThisBuild / organization := "morecat"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
)

// 純粋ドメイン: Article イベント ADT・値オブジェクト(Iron)・projection fold・JSON codec。
// IO(Firestore/Postgres/tapir)への依存を載せないことをビルドで強制する。
// RMU(Rust) とはコードを共有せず、イベント wire スキーマを契約フィクスチャで整合させる。
lazy val domain = project
  .in(file("domain"))
  .settings(
    name := "morecat-domain",
    libraryDependencies ++= Seq(
      "dev.zio"            %% "zio-json"      % zioJson,
      "io.github.iltotore" %% "iron"          % ironVersion,
      "io.github.iltotore" %% "iron-zio-json" % ironVersion,
      "dev.zio"            %% "zio-test"      % zioVersion % Test,
      "dev.zio"            %% "zio-test-sbt"  % zioVersion % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )

lazy val root = (project in file("."))
  .aggregate(domain)
  .settings(
    name := "morecat-api",
    publish / skip := true,
  )
