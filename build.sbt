import Dependencies.Libraries
import sbt.Keys.scalacOptions
import sbtprotoc.ProtocPlugin.autoImport.PB

import scala.collection.Seq

ThisBuild / version := "1.0.0-SNAPSHOT"

ThisBuild / scalaVersion     := "3.7.0" // update to 3.7.+ when libraries are ready
ThisBuild / organization     := "com.monadial"
ThisBuild / organizationName := "Monadial"

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

ThisBuild / homepage := Some(url("https://waygrid.monadial.com"))
ThisBuild / licenses := Seq(License.GPL3_or_later) // see LICENSE file in the root of the project
ThisBuild / developers := List(
  Developer("tmihalicka", "Tomas Mihalicka", "tomas@monadial.com", url("https://monadial.com"))
)

ThisBuild / scalacOptions ++= Seq(
  "-source:3.7", // @see https://scala-lang.org/2024/08/19/given-priority-change-3.7.html#migrating-to-the-new-prioritization
  "-Xprint-suspension"
)

ThisBuild / semanticdbEnabled := true

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
  buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion),
  buildInfoPackage := s"com.monadial.waygrid.${component}.${service}"
)

def configureOtel(component: String, service: String): Seq[Setting[?]] = Seq(
  Compile / javaOptions ++= Seq(
    "-Dotel.java.global-autoconfigure.enabled=true",
    s"-Dotel.service.name=${component}-${service}",
    "-Dotel.propagators=b3multi",
    "-Dotel.exporter.otlp.endpoint=otel-collector-otel-collector-gateway.waygrid-observability.svc.cluster.local:8888"
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "Waygrid",
    Universal / javaOptions ++= (Compile / javaOptions).value,
    fork              := true,
    scalafmtOnCompile := true,
    scalafixOnCompile := true
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
      // scodec
      Libraries.scodecCore.value,
      // jsoniter
      Libraries.jsoniterScalaCore.value,
      Libraries.jsoniterScalaMacros.value,
      // refined
      Libraries.refined.value,
      Libraries.refinedCats.value,
      // uri
      Libraries.http4sCore,
      // crypto
      Libraries.zeroAllocationHashing.value,
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
  .enablePlugins(Http4sGrpcPlugin)
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
      // sKema
      Libraries.jsonsKema.value,
      // actors
      Libraries.catsActors.value,
      // shapeless
      Libraries.shapeless3Typeable.value,
      // fs2
      Libraries.fs2Kafka.value,
      // scodec
      Libraries.scodecBits.value,
      // http4s
      Libraries.http4sDsl,
      Libraries.http4sServer,
      Libraries.http4sCirce,
      Libraries.http4sOtel4sCore,
      Libraries.http4sOtel4sMetrics,
      Libraries.http4sOtel4sTraceCore,
      Libraries.http4sOtel4sTraceServer,
      Libraries.http4sOtel4sTraceClient,
      // scalapb
      Libraries.scalaPb,
      // otel4s
      Libraries.otel4sOtelJava.value,
      Libraries.otel4InstrumentationMetrics.value,
      Libraries.opentelemetryExporterOtlp.value,
      Libraries.opentelemetrySdkExtensionAutoconfigure.value,
      Libraries.opentelemetryInstrumentation.value,
      // redis
      Libraries.redis4CatsEffects.value,
      Libraries.redis4CatsStream.value,
      // database
      Libraries.doobieCore.value,
      Libraries.doobieHikari.value,
      Libraries.doobiePostgres.value,
      Libraries.doobiePostgresCirce.value,
      Libraries.doobieFlyway.value,
      Libraries.flywayPostgres.value,
      // tests
      Libraries.catsLaws         % Test,
      Libraries.monocleLaw       % Test,
      Libraries.scalacheck       % Test,
      Libraries.weaverCats       % Test,
      Libraries.weaverDiscipline % Test,
      Libraries.weaverScalaCheck % Test
    ) ++ Seq(
      // this is needed to dns resolver correctly work on Apple Silicon (M1...)
      "io.netty" % "netty-resolver-dns-native-macos" % "4.2.7.Final" % Compile classifier "osx-aarch_64"
    )
  }
  .settings {
    Compile / PB.targets ++= Seq(
      // set grpc = false because http4s-grpc generates its own code
      scalapb.gen(grpc = false, scala3Sources = true) -> (Compile / sourceManaged).value / "scalapb"
    )
    Compile / scalacOptions ++= {
      val managed = (Compile / sourceManaged).value.getAbsolutePath
      Seq(
        s"-Wconf:src=$managed/.*scalapb/.*:silent",
        s"-Wconf:src=$managed/.*http4s-grpc/.*:silent"
      )
    }
  }
  .dependsOn(`common-domain`)

//
//
// SYSTEM
lazy val `system-common` = (project in file("modules/system/system-common"))
  .dependsOn(`common-application`)

lazy val `system-topology` = (project in file("modules/system/system-topology"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "topology") *)
  .settings(buildInfo("system", "topology") *)
  .dependsOn(`system-common`)

lazy val `system-waystation` = (project in file("modules/system/system-waystation"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "waystation") *)
  .settings(buildInfo("system", "waystation") *)
  .settings(configureOtel("system", "waystation") *)
  .settings(
    libraryDependencies ++= List(
      Libraries.catsLaws         % Test,
      Libraries.monocleLaw       % Test,
      Libraries.scalacheck       % Test,
      Libraries.weaverCats       % Test,
      Libraries.weaverDiscipline % Test,
      Libraries.weaverScalaCheck % Test
    )
  )
  .dependsOn(`system-common`)

lazy val `system-history` = (project in file("modules/system/system-history"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "history") *)
  .settings(buildInfo("system", "history") *)
  .dependsOn(`system-common`)

lazy val `system-scheduler` = (project in file("modules/system/system-scheduler"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "scheduler") *)
  .settings(buildInfo("system", "scheduler") *)
  .dependsOn(`system-common`)

lazy val `system-dag-registry` = (project in file("modules/system/system-dag-registry"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "dag-registry") *)
  .settings(buildInfo("system", "dag-registry") *)
  .dependsOn(`system-common`)

lazy val `system-secure-store` = (project in file("modules/system/system-secure-store"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "secure-store") *)
  .settings(buildInfo("system", "secure-store") *)
  .dependsOn(`system-common`)

lazy val `system-iam` = (project in file("modules/system/system-iam"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "iam") *)
  .settings(buildInfo("system", "iam") *)
  .dependsOn(`system-common`)

lazy val `system-billing` = (project in file("modules/system/system-billing"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "billing") *)
  .settings(buildInfo("system", "billing") *)
  .dependsOn(`system-common`)

lazy val `system-kms` = (project in file("modules/system/system-kms"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "kms") *)
  .settings(buildInfo("system", "kms") *)
  .dependsOn(`system-common`)

lazy val `system-k8s-operator` = (project in file("modules/system/system-k8s-operator"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .dependsOn(`system-common`)

//
//
// ORIGIN
lazy val `origin-http` = (project in file("modules/origin/origin-http"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "http") *)
  .settings(buildInfo("origin", "http") *)
  .settings(configureOtel("origin", "http") *)
  .dependsOn(`common-application`)

lazy val `origin-grpc` = (project in file("modules/origin/origin-grpc"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "grpc") *)
  .settings(buildInfo("origin", "grpc") *)
  .dependsOn(`common-application`)

lazy val `origin-kafka` = (project in file("modules/origin/origin-kafka"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "kafka") *)
  .settings(buildInfo("origin", "kafka") *)
  .dependsOn(`common-application`)
//
//
// DESTINATION
lazy val `destination-webhook` = (project in file("modules/destination/destination-webhook"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "webhook") *)
  .settings(buildInfo("destination", "webhook") *)
  .dependsOn(`common-application`)

lazy val `destination-websocket` = (project in file("modules/destination/destination-websocket"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "websocket") *)
  .settings(buildInfo("destination", "websocket") *)
  .dependsOn(`common-application`)

lazy val `destination-blackhole` = (project in file("modules/destination/destination-blackhole"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "blackhole") *)
  .settings(buildInfo("destination", "blackhole") *)
  .dependsOn(`common-application`)

//
//
// PROCESSOR
lazy val `processor-openai` = (project in file("modules/processor/processor-openai"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("processor", "openai") *)
  .settings(buildInfo("processor", "openai") *)
  .dependsOn(`common-application`)

lazy val `processor-lambda` = (project in file("modules/processor/processor-lambda"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("processor", "lambda") *)
  .settings(buildInfo("processor", "lambda") *)
