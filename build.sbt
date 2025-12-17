import Dependencies.Libraries
import sbt.Keys.scalacOptions

import scala.collection.Seq

// ======================================================
// Global build settings
// ======================================================

ThisBuild / version          := "1.0.0-SNAPSHOT"
ThisBuild / scalaVersion     := "3.7.4"
ThisBuild / organization     := "com.monadial"
ThisBuild / organizationName := "Monadial"

ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

ThisBuild / homepage := Some(url("https://waygrid.dev"))
ThisBuild / licenses := Seq(License.GPL3_or_later)
ThisBuild / developers := List(
  Developer(
    "tmihalicka",
    "Tomas Mihalicka",
    "tomas@monadial.com",
    url("https://monadial.com")
  )
)

ThisBuild / scalacOptions ++= Seq(
  "-source:3.7",
  "-Xprint-suspension"
)

ThisBuild / javaOptions ++= Seq(
  "--sun-misc-unsafe-memory-access=allow" // TODO: resolve for Java 24
)

ThisBuild / semanticdbEnabled := true

// ======================================================
// Coverage
// ======================================================

ThisBuild / coverageEnabled           := false
ThisBuild / coverageFailOnMinimum     := false
ThisBuild / coverageHighlighting      := true
ThisBuild / coverageExcludedPackages  := "<empty>;.*BuildInfo.*;.*scalapb.*"

// ======================================================
// Global Docker defaults
// ======================================================

ThisBuild / Docker / dockerBaseImage  := "eclipse-temurin:23"
ThisBuild / Docker / maintainer       := "Monadial"
ThisBuild / Docker / daemonUser       := "monadial"
ThisBuild / Docker / dockerUsername   := Some("monadial")
ThisBuild / Docker / dockerExposedPorts ++= Seq(1337)

// ======================================================
// sbt lint exclusions
// ======================================================

Global / excludeLintKeys ++= Set(
  buildInfoKeys,
  buildInfoPackage,
  Docker / dockerBaseImage,
  Docker / maintainer,
  Docker / daemonUser
).map(_.scopedKey)

// ======================================================
// Helpers
// ======================================================

def dockerImage(component: String, service: String): Seq[Setting[?]] =
  Seq(
    Docker / packageName := s"waygrid-$component-$service"
  )

def buildInfo(component: String, service: String): Seq[Setting[?]] =
  Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage :=
      s"com.monadial.waygrid.$component.${service.replace("-", ".")}"
  )

def configureOtel(component: String, service: String): Seq[Setting[?]] =
  Seq(
    Compile / javaOptions ++= Seq(
      "-Dotel.java.global-autoconfigure.enabled=true",
      s"-Dotel.service.name=$component-$service"
    )
  )

// ======================================================
// Root project (aggregator only)
// ======================================================

lazy val root = (project in file("."))
  .settings(
    name := "Waygrid",
    fork := true,
    scalafmtOnCompile := true,
    scalafixOnCompile := true
  )

// ======================================================
// COMMON
// ======================================================

lazy val `common-domain` = (project in file("modules/common/domain"))
  .settings(
    libraryDependencies ++= List(
      Libraries.cats.value,
      Libraries.catsParse.value,
      Libraries.kittens.value,
      Libraries.monocleCore.value,
      Libraries.monocleMacro.value,
      Libraries.airframeUlid.value,
      Libraries.ip4sCore.value,
      Libraries.fs2Core.value,
      Libraries.circeCore.value,
      Libraries.circeRefined.value,
      Libraries.jsoniterScalaCore.value,
      Libraries.jsoniterScalaMacros.value,
      Libraries.refined.value,
      Libraries.refinedCats.value,
      Libraries.http4sCore,
      Libraries.zeroAllocationHashing.value,
      Libraries.catsLaws         % Test,
      Libraries.monocleLaw       % Test,
      Libraries.scalacheck       % Test,
      Libraries.weaverCats       % Test,
      Libraries.weaverDiscipline % Test,
      Libraries.weaverScalaCheck % Test
    )
  )

lazy val `common-application` =
  (project in file("modules/common/application"))
    .enablePlugins(Http4sGrpcPlugin)
    .settings(
      libraryDependencies ++= List(
        Libraries.bouncyCastle.value,
        Libraries.odinCore.value,
        Libraries.catsEffect.value,
        Libraries.circeCore.value,
        Libraries.circeGeneric.value,
        Libraries.circeParser.value,
        Libraries.circeRefined.value,
        Libraries.circeConfig.value,
        Libraries.jsonsKema.value,
        Libraries.catsActors.value,
        Libraries.shapeless3Typeable.value,
        Libraries.fs2Kafka.value,
        Libraries.fs2AwsCore.value,
        Libraries.scodecBits.value,
        Libraries.http4sDsl,
        Libraries.http4sServer,
        Libraries.http4sCirce,
        Libraries.http4sOtel4sCore,
        Libraries.http4sOtel4sMetrics,
        Libraries.http4sOtel4sTraceCore,
        Libraries.http4sOtel4sTraceServer,
        Libraries.http4sOtel4sTraceClient,
        Libraries.scalaPb,
        Libraries.otel4sOtelJava.value,
        Libraries.otel4sExperimentalMetrics.value,
        Libraries.otel4InstrumentationMetrics.value,
        Libraries.opentelemetryExporterOtlp.value,
        Libraries.opentelemetrySdkExtensionAutoconfigure.value,
        Libraries.opentelemetryInstrumentation.value,
        Libraries.redis4CatsEffects.value,
        Libraries.redis4CatsStream.value,
        Libraries.doobieCore.value,
        Libraries.doobieHikari.value,
        Libraries.doobiePostgres.value,
        Libraries.doobiePostgresCirce.value,
        Libraries.doobieFlyway.value,
        Libraries.flywayPostgres.value,
        Libraries.clickhouseJdbc.value,
        Libraries.catsLaws         % Test,
        Libraries.monocleLaw       % Test,
        Libraries.scalacheck       % Test,
        Libraries.weaverCats       % Test,
        Libraries.weaverDiscipline % Test,
        Libraries.weaverScalaCheck % Test,
        "io.netty" % "netty-resolver-dns-native-macos" % "4.2.7.Final" %
          Compile classifier "osx-aarch_64"
      )
    )
    .settings(
      Compile / PB.targets ++= Seq(
        scalapb.gen(grpc = false, scala3Sources = true) ->
          (Compile / sourceManaged).value / "scalapb"
      ),
      Compile / scalacOptions ++= {
        val managed = (Compile / sourceManaged).value.getAbsolutePath
        Seq(
          s"-Wconf:src=$managed/.*scalapb/.*:silent",
          s"-Wconf:src=$managed/.*http4s-grpc/.*:silent"
        )
      }
    )
    .dependsOn(`common-domain`)

//
//
// SYSTEM
lazy val `system-common` = (project in file("modules/system/common"))
  .dependsOn(`common-application`)

lazy val `system-topology` = (project in file("modules/system/topology"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "topology") *)
  .settings(buildInfo("system", "topology") *)
  .dependsOn(`system-common`)

lazy val `system-waystation` = (project in file("modules/system/waystation"))
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

lazy val `system-history` = (project in file("modules/system/history"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "history") *)
  .settings(buildInfo("system", "history") *)
  .dependsOn(`system-common`)

lazy val `system-scheduler` = (project in file("modules/system/scheduler"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "scheduler") *)
  .settings(buildInfo("system", "scheduler") *)
  .dependsOn(`system-common`)

lazy val `system-dag-registry` = (project in file("modules/system/dag-registry"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "dag-registry") *)
  .settings(buildInfo("system", "dag-registry") *)
  .dependsOn(`system-common`)

lazy val `system-secure-store` = (project in file("modules/system/secure-store"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "secure-store") *)
  .settings(buildInfo("system", "secure-store") *)
  .dependsOn(`system-common`)

lazy val `system-blob-store` = (project in file("modules/system/blob-store"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "secure-blob-store") *)
  .settings(buildInfo("system", "secure-blob-store") *)
  .dependsOn(`system-common`)
  .settings(
    libraryDependencies ++= List(
      Libraries.fs2AwsS3.value,
      Libraries.fs2AwsS3Tagless.value
    )
  )

lazy val `system-iam` = (project in file("modules/system/iam"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "iam") *)
  .settings(buildInfo("system", "iam") *)
  .dependsOn(`system-common`)

lazy val `system-billing` = (project in file("modules/system/billing"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "billing") *)
  .settings(buildInfo("system", "billing") *)
  .dependsOn(`system-common`)

lazy val `system-kms` = (project in file("modules/system/kms"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("system", "kms") *)
  .settings(buildInfo("system", "kms") *)
  .dependsOn(`system-common`)

lazy val `infrastructure-k8s-operator` = (project in file("modules/infrastructure/k8s-operator"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .dependsOn(`system-common`)

//
//
// ORIGIN
lazy val `origin-http` = (project in file("modules/origin/http"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "http") *)
  .settings(buildInfo("origin", "http") *)
  .settings(configureOtel("origin", "http") *)
  .dependsOn(`common-application`)

lazy val `origin-grpc` = (project in file("modules/origin/grpc"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "grpc") *)
  .settings(buildInfo("origin", "grpc") *)
  .dependsOn(`common-application`)

lazy val `origin-kafka` = (project in file("modules/origin/kafka"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("origin", "kafka") *)
  .settings(buildInfo("origin", "kafka") *)
  .dependsOn(`common-application`)
//
//
// DESTINATION
lazy val `destination-webhook` = (project in file("modules/destination/webhook"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "webhook") *)
  .settings(buildInfo("destination", "webhook") *)
  .dependsOn(`common-application`)

lazy val `destination-websocket` = (project in file("modules/destination/websocket"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "websocket") *)
  .settings(buildInfo("destination", "websocket") *)
  .dependsOn(`common-application`)

lazy val `destination-blackhole` = (project in file("modules/destination/blackhole"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("destination", "blackhole") *)
  .settings(buildInfo("destination", "blackhole") *)
  .dependsOn(`common-application`)

//
//
// PROCESSOR
lazy val `processor-openai` = (project in file("modules/processor/openai"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("processor", "openai") *)
  .settings(buildInfo("processor", "openai") *)
  .dependsOn(`common-application`)

lazy val `processor-lambda` = (project in file("modules/processor/lambda"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
  .settings(dockerImage("processor", "lambda") *)
  .settings(buildInfo("processor", "lambda") *)
