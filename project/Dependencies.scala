import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import sbt.{Def, *}

object Dependencies {
  object V {
    val airframeUlid = "24.8.0"
    val bouncyCastleVersion = "1.78.1"
    val cats = "2.12.0"
    val catsEffect = "3.5.4"
    val circe = "0.14.10"
    val circeConfig = "0.10.1"
    val circeRefined = "0.14.9"
    val cloudEvents = "2.5.0"
    val fs2core = "3.11.0"
    val fs2Kafka = "3.5.1"
    val http4s = "1.0.0-M41"
    val http4sMetrics = "1.0.0-M40"
    val http4sWs = "1.0.0-M40"
    val ip4s = "3.6.0"
    val kittens = "3.4.0"
    val log4cats = "2.6.0"
    val logbackClassic = "1.5.8"
    val monocle = "3.3.0"
    val odin = "0.13.0"
    val odinContrib = "1.0.2"
    val opentelemetryInstrumentation = "2.7.0-alpha"
    val opentelemetryOtlp = "1.42.0"
    val otel4s = "0.9.0"
    val redis4Cats = "1.7.1"
    val scalacheck = "1.18.0"
    val typesafeConfig = "1.4.3"
    val weaver     = "0.8.4"
    val zeroAllocationHashingVersion = "0.16"
    val catsActors = "2.0.0"
    val skunk = "0.6.4"
    val fly4s = "1.1.0"
    val prometheus4cats = "3.0.0"
  }

  object Libraries {
    type Def = Def.Initialize[sbt.ModuleID]

    def circe(artifact: String, version: String): Def = Def.setting("io.circe" %%% s"circe-$artifact" % version)
    def http4s(artifact: String): ModuleID = "org.http4s" %% s"http4s-$artifact" % V.http4s

    val cats = Def.setting("org.typelevel" %%% "cats-core" % V.cats)
    val catsEffect = Def.setting("org.typelevel" %%% "cats-effect" % V.catsEffect)

    val fs2Core = Def.setting("co.fs2" %%% "fs2-core" % V.fs2core)
    val fs2Kafka = Def.setting("com.github.fd4s" %%% "fs2-kafka" % V.fs2Kafka)

    val kittens = Def.setting("org.typelevel" %%% "kittens" % V.kittens)

    val monocleCore = Def.setting("dev.optics" %% "monocle-core" % V.monocle)
    val monocleMacro = Def.setting("dev.optics" %% "monocle-macro" % V.monocle)

    val circeCore: Def = circe("core", V.circe)
    val circeGeneric: Def = circe("generic", V.circe)
    val circeParser: Def = circe("parser", V.circe)
    val circeRefined: Def = circe("refined", V.circeRefined)
    val circeConfig: Def = circe("config", V.circeConfig)
    val circeTesting: Def = circe("testing", V.circe)

    val http4sDsl = http4s("dsl")
    val http4sServer = http4s("ember-server")
    val http4sCirce = http4s("circe")

    val http4sJdkWs = "org.http4s" %% "http4s-jdk-http-client" % V.http4sWs
    val http4sMetrics = "org.http4s" %% "http4s-prometheus-metrics" % V.http4sMetrics

    val ip4sCore = Def.setting("com.comcast" %%% "ip4s-core" % V.ip4s)

    val typesafeConfig = Def.setting("com.typesafe" % "config" % V.typesafeConfig)

    val airframeUlid = Def.setting("org.wvlet.airframe" %% "airframe-ulid" % V.airframeUlid)

    val logBack = Def.setting("ch.qos.logback" % "logback-classic" % V.logbackClassic)
    val log4CatsCore = Def.setting("org.typelevel" %% "log4cats-core" % V.log4cats)
    val log4CatsSlf4j = Def.setting("org.typelevel" %% "log4cats-slf4j" % V.log4cats)

    val bouncyCastle = Def.setting("org.bouncycastle" % "bcprov-jdk18on" % V.bouncyCastleVersion)

    val zeroAllocationHashing = Def.setting("net.openhft" % "zero-allocation-hashing" % V.zeroAllocationHashingVersion)

    val otel4sOtelJava = Def.setting("org.typelevel" %% "otel4s-oteljava" % V.otel4s)
    val opentelemetryExporterOtlp = Def.setting("io.opentelemetry" % "opentelemetry-exporter-otlp" % V.opentelemetryOtlp)
    val opentelemetrySdkExtensionAutoconfigure = Def.setting("io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % V.opentelemetryOtlp)
    val opentelemetryInstrumentation = Def.setting("io.opentelemetry.instrumentation" % "opentelemetry-runtime-telemetry-java17" % V.opentelemetryInstrumentation)

    val redis4CatsEffects = Def.setting("dev.profunktor" %% "redis4cats-effects" % V.redis4Cats)
    val redis4CatsStream = Def.setting("dev.profunktor" %% "redis4cats-streams" % V.redis4Cats)
    val redis4CatsLog4cats = Def.setting("dev.profunktor" %% "redis4cats-log4cats" % V.redis4Cats)

    val skunkCore = Def.setting("org.tpolecat" %% "skunk-core" % V.skunk)
    val fly4s = Def.setting("com.github.geirolz" %% "fly4s" % V.fly4s)

    val catsActors = Def.setting("com.github.suprnation.cats-actors" %% "cats-actors" % V.catsActors)

    // test
    val catsLaws         = "org.typelevel"       %% "cats-laws"         % V.cats
    val monocleLaw       = "dev.optics"          %% "monocle-law"       % V.monocle
    val scalacheck       = "org.scalacheck"      %% "scalacheck"        % V.scalacheck
    val weaverCats       = "com.disneystreaming" %% "weaver-cats"       % V.weaver
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % V.weaver
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % V.weaver
  }
}
