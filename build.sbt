ThisBuild / tlBaseVersion := "0.4"

ThisBuild / organization := "io.github.arturaz"
ThisBuild / organizationName := "arturaz"

ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers ++= List(
  tlGitHubDev("arturaz", "Artūras Šlajus")
)

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / scalacOptions ++= Seq(
  "-language:implicitConversions",
  "-Werror"
)

ThisBuild / tlSitePublishBranch := Some("main")

// Disable the checks, I don't want to deal with them right now.
ThisBuild / tlCiHeaderCheck := false

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))

lazy val root = tlCrossRootProject.aggregate(core, tests)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "doobie-typesafe",
    description := "Typesafe table definitions for doobie",
    libraryDependencies ++= Seq(
      // https://mvnrepository.com/artifact/org.tpolecat/doobie-core
      // RC6 is broken and should not be used until RC7: https://github.com/typelevel/doobie/milestone/15
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC5"
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
      // https://mvnrepository.com/artifact/org.scalameta/munit
      "org.scalameta" %% "munit" % "1.0.3" % Test,
      // https://mvnrepository.com/artifact/org.typelevel/munit-cats-effect
      "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test,
      // https://mvnrepository.com/artifact/com.h2database/h2
      "com.h2database" % "h2" % "2.3.232" % Test
    )
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(core)
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    scalacOptions --= Seq(
      // Disable unused import warnings for the docs as they report false positives.
      "-Wunused:imports"
    )
  )
