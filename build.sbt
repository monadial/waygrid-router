import Dependencies.Libraries
import sbt.Keys.scalacOptions

import scala.collection.Seq

ThisBuild / version := "1.0.0-SNAPSHOT"

ThisBuild / scalaVersion     := "3.6.3"
ThisBuild / organization     := "com.monadial"
ThisBuild / organizationName := "Monadial"

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

ThisBuild / homepage := Some(url("https://waygrid.monadial.com"))
ThisBuild / licenses := Seq(License.GPL3_or_later) // see LICENSE file in the root of the project
ThisBuild / developers := List(
  Developer("tmihalicka", "Tomas Mihalicka", "tomas@monadial.com", url("https://monadial.com"))
)

ThisBuild / scalacOptions ++= Seq(
  "-source:3.7" // @see https://scala-lang.org/2024/08/19/given-priority-change-3.7.html#migrating-to-the-new-prioritization
)

//
//
// UTIL
def dockerImage(component: String, service: String): Seq[Setting[?]] = Seq(
  Docker / packageName     := s"waygrid-$component-$service",
  Docker / maintainer      := "Monadial",
  Docker / daemonUser      := "monadial",
  Docker / dockerBaseImage := "eclipse-temurin:23",
  Docker / dockerUsername  := Some("monadial"),
  Docker / dockerExposedPorts ++= Seq(1337) // 1337 is default port for all Waygrid services
)

def buildInfo(component: String, service: String) = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion),
  buildInfoPackage := s"com.monadial.waygrid.${component}.${service}"
)

lazy val root = (project in file("."))
  .settings(
    name := "Waygrid",
  )

//
//
// COMMON
lazy val `common-domain` = (project in file("modules/common/common-domain"))
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
      // fs2
      Libraries.fs2Core.value,
      // circe
      Libraries.circeCore.value,
      Libraries.circeRefined.value,
      // refined
      Libraries.refined.value,
      Libraries.refinedCats.value,
      // tests
      Libraries.catsLaws         % Test,
      Libraries.monocleLaw       % Test,
      Libraries.scalacheck       % Test,
      Libraries.weaverCats       % Test,
      Libraries.weaverDiscipline % Test,
      Libraries.weaverScalaCheck % Test
    )
  }

lazy val `common-application` = (project in file("modules/common/common-application"))
  .settings {
    libraryDependencies ++= List(
      // odin
      Libraries.odinCore.value,
      // cats
      Libraries.catsEffect.value,
      // circe
      Libraries.circeCore.value,
      Libraries.circeGeneric.value,
      Libraries.circeParser.value,
      Libraries.circeRefined.value,
      Libraries.circeConfig.value,
      // actorss
      Libraries.catsActors.value,
      // fs2
      Libraries.fs2Kafka.value,
      // http4s
      Libraries.http4sDsl,
      Libraries.http4sServer,
      Libraries.http4sCirce,
      // tests
      Libraries.catsLaws         % Test,
      Libraries.monocleLaw       % Test,
      Libraries.scalacheck       % Test,
      Libraries.weaverCats       % Test,
      Libraries.weaverDiscipline % Test,
      Libraries.weaverScalaCheck % Test
    )
  }
  .dependsOn(`common-domain`)

//
//
// SYSTEM
lazy val `system-topology` = (project in file("modules/system/system-topology"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "topology")*)
  .settings(buildInfo("system", "topology")*)
  .dependsOn(`common-application`)

lazy val `system-waystation` = (project in file("modules/system/system-waystation"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "waystation")*)
  .settings(buildInfo("system", "waystation")*)
  .dependsOn(`common-application`)

lazy val `system-history` = (project in file("modules/system/system-history"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "history")*)
  .settings(buildInfo("system", "history")*)
  .dependsOn(`common-application`)

lazy val `system-scheduler` = (project in file("modules/system/system-scheduler"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "scheduler")*)
  .settings(buildInfo("system", "scheduler")*)
  .dependsOn(`common-application`)

lazy val `system-k8s-operator` = (project in file("modules/system/system-k8s-operator"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .dependsOn(`common-application`)

//
//
// ORIGIN
lazy val `origin-http` = (project in file("modules/origin/origin-http"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "http")*)
  .dependsOn(`common-application`)

//
//
// DESTINATION
lazy val `destination-webhook` = (project in file("modules/destination/destination-webhook"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "webhook")*)
  .settings(buildInfo("destination", "webhook")*)
  .dependsOn(`common-application`)

lazy val `destination-websocket` = (project in file("modules/destination/destination-websocket"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "websocket")*)
  .settings(buildInfo("destination", "websocket")*)
  .dependsOn(`common-application`)

lazy val `destination-blackhole` = (project in file("modules/destination/destination-blackhole"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "blackhole")*)
  .settings(buildInfo("destination", "blackhole")*)
  .dependsOn(`common-application`)

//
//
// PROCESSOR
lazy val `processor-openai` = (project in file("modules/processor/processor-openai"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("processor", "openai")*)
  .settings(buildInfo("processor", "openai")*)
  .dependsOn(`common-application`)
