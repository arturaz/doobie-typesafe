ThisBuild / tlBaseVersion := "0.1"

ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers ++= List(
  tlGitHubDev("arturaz", "Artūras Šlajus")
)

ThisBuild / scalaVersion := "3.3.0"
ThisBuild / scalacOptions ++= Seq(
  "-language:implicitConversions"
)

ThisBuild / tlSitePublishBranch := Some("main")

// Disable the checks, I don't want to deal with them right now.
ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlCiScalafmtCheck := false

lazy val root = tlCrossRootProject.aggregate(core, tests)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "doobie-typesafe",
    description := "Typesafe table definitions for doobie",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC4"
    )
  )

lazy val tests = project
  .in(file("tests"))
  .enablePlugins(NoPublishPlugin)
  .dependsOn(core)
  .settings(
    name := "doobie-typesafe-tests",
    description := "Tests for doobie-typesafe",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0-M8" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.0.0-M3" % Test,
      "com.h2database" % "h2" % "2.1.214" % Test,
    )
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(core)
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    scalacOptions --= Seq(
      // Disable unused import warnings for the docs as they report false positives.
      "-Wunused:imports",
    )
  )