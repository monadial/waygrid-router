import Dependencies.Libraries

ThisBuild / version := "1.0.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.3"
ThisBuild / organization := "com.monadial"
ThisBuild / organizationName := "Monadial"

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

lazy val root = (project in file("."))
  .settings(
    name := "Waygrid",
    idePackagePrefix := Some("com.monadial.waygrid")
  )

lazy val `common-model` = (project in file("modules/common-model"))
  .settings {
    libraryDependencies ++= List(
      // cats
      Libraries.cats.value,
      Libraries.kittens.value,
      // monocle
      Libraries.monocleCore.value,
      Libraries.monocleMacro.value,
      // misc
      Libraries.airframeUlid.value,
      Libraries.ip4sCore.value,
    )
  }

lazy val `common-lib` = (project in file("modules/common-lib"))
  .settings {
    libraryDependencies ++= List(
      // cats
      Libraries.catsEffect.value,
      // circe
      Libraries.circeCore.value,
      Libraries.circeGeneric.value,
      Libraries.circeParser.value,
      Libraries.circeRefined.value,
      Libraries.circeConfig.value,
    )
  }
  .dependsOn(`common-model`)

lazy val `system-waystation` = (project in file("modules/system-waystation"))
  .dependsOn(`common-lib`)

lazy val `system-archive` = (project in file("modules/system-archive"))
  .dependsOn(`common-lib`)

lazy val `origin-http` = (project in file("modules/origin-http"))
  .dependsOn(`common-lib`)

lazy val `destination-webhook` = (project in file("modules/destination-webhook"))
  .dependsOn(`common-lib`)

lazy val `destination-blackhole` = (project in file("modules/destination-blackhole"))
  .dependsOn(`common-lib`)