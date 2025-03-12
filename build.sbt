import Dependencies.Libraries

ThisBuild / version := "1.1.0-SNAPSHOT"

ThisBuild / scalaVersion     := "3.6.3"
ThisBuild / organization     := "com.monadial"
ThisBuild / organizationName := "Monadial"

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

lazy val root = (project in file("."))
  .settings(
    name := "Waygrid"
  )

//
//
// COMMON
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
      Libraries.ip4sCore.value
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
      Libraries.circeConfig.value
    )
  }
  .dependsOn(`common-model`)

//
//
// SYSTEM
lazy val `system-waystation` = (project in file("modules/system-waystation"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("system", "waystation")*)
  .dependsOn(`common-lib`)

lazy val `system-history` = (project in file("modules/system-history"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("system", "history")*)
  .dependsOn(`common-lib`)

lazy val `system-scheduler` = (project in file("modules/system-scheduler"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("system", "scheduler")*)
  .dependsOn(`common-lib`)

//
//
// ORIGIN
lazy val `origin-http` = (project in file("modules/origin-http"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("origin", "http")*)
  .dependsOn(`common-lib`)

//
//
// DESTINATION
lazy val `destination-webhook` = (project in file("modules/destination-webhook"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("destination", "webhook")*)
  .dependsOn(`common-lib`)

lazy val `destination-websocket` = (project in file("modules/destination-websocket"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("destination", "websocket")*)
  .dependsOn(`common-lib`)

lazy val `destination-blackhole` = (project in file("modules/destination-blackhole"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerBuild("destination", "blackhole")*)
  .dependsOn(`common-lib`)

//
//
// UTIL
def dockerBuild(component: String, service: String): Seq[Setting[?]] = Seq(
  Docker / packageName     := s"waygrid-$component-$service",
  Docker / maintainer      := "Monadial",
  Docker / daemonUser      := "monadial",
  Docker / dockerBaseImage := "eclipse-temurin:23",
  Docker / dockerUsername  := Some("monadial"),
  Docker / dockerExposedPorts ++= Seq(1337) // 1337 is default port for all waygrid services
)
