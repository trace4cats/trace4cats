lazy val commonSettings = Seq(
  scalaVersion := Dependencies.Versions.scala213,
  organization := "io.janstenpickle",
  organizationName := "janstenpickle",
  developers := List(
    Developer(
      "janstenpickle",
      "Chris Jansen",
      "janstenpickle@users.noreply.github.com",
      url = url("https://github.com/janstepickle")
    )
  ),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("https://github.com/janstenpickle/trace4cats")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/janstenpickle/trace4cats"), "scm:git:git@github.com:janstenpickle/trace4cats.git")
  ),
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.11.0").cross(CrossVersion.patch)),
  libraryDependencies ++= Seq(Dependencies.cats, Dependencies.collectionCompat),
  bintrayRepository := "trace4cats",
  releaseEarlyWith := BintrayPublisher,
  releaseEarlyEnableSyncToMaven := false,
  pgpPublicRing := file("./.github/git adlocal.pubring.asc"),
  pgpSecretRing := file("./.github/local.secring.asc"),
  crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala212)
)

lazy val noPublishSettings = commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None)

lazy val publishSettings = commonSettings ++ Seq(publishMavenStyle := true, pomIncludeRepository := { _ =>
  false
}, publishArtifact in Test := false)

lazy val graalSettings = Seq(
  graalVMNativeImageOptions ++= Seq(
    "--verbose",
    "--no-server",
    "--no-fallback",
    "--enable-http",
    "--enable-https",
    "--enable-all-security-services",
    "--report-unsupported-elements-at-runtime",
    "--allow-incomplete-classpath",
    "-Djava.net.preferIPv4Stack=true",
    "-H:IncludeResources='.*'",
    "-H:+ReportExceptionStackTraces",
    "-H:+ReportUnsupportedElementsAtRuntime",
    "-H:+TraceClassInitialization",
    "-H:+PrintClassInitialization",
    "-H:+RemoveSaturatedTypeFlows",
    "-H:+StackTrace",
    "-H:+JNI",
    "-H:-SpawnIsolates",
    "-H:-UseServiceLoaderFeature",
    "-H:ConfigurationFileDirectories=../../native-image/",
    "--install-exit-handlers",
    "--initialize-at-build-time=scala.runtime.Statics$VM",
    "--initialize-at-build-time=scala.Symbol$",
    "--initialize-at-build-time=ch.qos.logback",
    "--initialize-at-build-time=org.slf4j.LoggerFactory"
  )
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats")
  .aggregate(
    model,
    core,
    kernel,
    avro,
    `avro-exporter`,
    `avro-server`,
    `avro-test`,
    `collector-common`,
    `datadog-http-exporter`,
    `exporter-common`,
    `exporter-http`,
    `log-exporter`,
    `jaeger-thrift-exporter`,
    `opentelemetry-common`,
    `opentelemetry-jaeger-exporter`,
    `opentelemetry-otlp-grpc-exporter`,
    `opentelemetry-otlp-http-exporter`,
    `stackdriver-common`,
    `stackdriver-grpc-exporter`,
    `stackdriver-http-exporter`,
    natchez
  )

lazy val model =
  (project in file("modules/model"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-model",
      libraryDependencies ++= Seq(
        Dependencies.enumeratum,
        Dependencies.enumeratumCats,
        Dependencies.commonsCodec,
        Dependencies.kittens
      )
    )

lazy val example = (project in file("modules/example"))
  .settings(noPublishSettings)
  .settings(name := "trace4cats-example", libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.logback))
  .dependsOn(
    model,
    kernel,
    core,
    natchez,
    `avro-exporter`,
    `log-exporter`,
    `jaeger-thrift-exporter`,
    `opentelemetry-jaeger-exporter`,
    `opentelemetry-otlp-grpc-exporter`,
    `opentelemetry-otlp-http-exporter`,
    `stackdriver-grpc-exporter`,
    `stackdriver-http-exporter`
  )

lazy val test = (project in file("modules/test"))
  .settings(noPublishSettings)
  .settings(name := "trace4cats-test", libraryDependencies ++= Dependencies.test)
  .dependsOn(model)

lazy val `avro-test` = (project in file("modules/avro-test"))
  .settings(noPublishSettings)
  .settings(
    name := "trace4cats-avro-test",
    libraryDependencies ++= Dependencies.test.map(_  % Test),
    libraryDependencies ++= Seq(Dependencies.logback % Test)
  )
  .dependsOn(model)
  .dependsOn(`avro-exporter`, `avro-server`, test % "test->compile")

lazy val kernel =
  (project in file("modules/kernel"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-kernel",
      libraryDependencies ++= Dependencies.test.map(_     % Test),
      libraryDependencies ++= Seq(Dependencies.catsEffect % Test)
    )
    .dependsOn(model, test % "test->compile")

lazy val core =
  (project in file("modules/core"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-core",
      libraryDependencies ++= Dependencies.test.map(_                                  % Test),
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.catsEffectLaws % Test)
    )
    .dependsOn(model, kernel, test % "test->compile")

lazy val avro =
  (project in file("modules/avro"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro",
      libraryDependencies ++= Seq(Dependencies.vulcan, Dependencies.vulcanGeneric, Dependencies.vulcanEnumeratum)
    )
    .dependsOn(model)

lazy val `log-exporter` =
  (project in file("modules/log-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-log-exporter",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.log4cats)
    )
    .dependsOn(model, kernel)

lazy val `jaeger-integration-test` =
  (project in file("modules/jaeger-integration-test"))
    .settings(noPublishSettings)
    .settings(
      name := "trace4cats-jaeger-integration-test",
      libraryDependencies ++= Dependencies.test,
      libraryDependencies ++= Seq(
        Dependencies.circeGeneric,
        Dependencies.http4sCirce,
        Dependencies.http4sEmberClient,
        Dependencies.logback,
        Dependencies.testContainers
      )
    )
    .dependsOn(kernel, test)

lazy val `jaeger-thrift-exporter` =
  (project in file("modules/jaeger-thrift-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-thrift-exporter",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.jaegerThrift)
    )
    .dependsOn(model, kernel, `exporter-common`, `jaeger-integration-test` % "test->compile")

lazy val `opentelemetry-common` =
  (project in file("modules/opentelemetry-common"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-opentelemetry-common",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.fs2,
        Dependencies.openTelemetrySdk,
        Dependencies.grpcApi
      )
    )
    .dependsOn(model, kernel, `exporter-common`)

lazy val `opentelemetry-jaeger-exporter` =
  (project in file("modules/opentelemetry-jaeger-exporter"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-opentelemetry-jaeger-exporter",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.fs2,
        Dependencies.openTelemetryJaegerExporter,
        Dependencies.grpcOkHttp % Test
      )
    )
    .dependsOn(model, kernel, `exporter-common`, `opentelemetry-common`, `jaeger-integration-test` % "test->compile")

lazy val `opentelemetry-otlp-grpc-exporter` =
  (project in file("modules/opentelemetry-otlp-grpc-exporter"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-opentelemetry-otlp-grpc-exporter",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.fs2,
        Dependencies.openTelemetryOtlpExporter,
        Dependencies.grpcOkHttp % Test
      )
    )
    .dependsOn(model, kernel, `exporter-common`, `opentelemetry-common`, `jaeger-integration-test` % "test->compile")

lazy val `opentelemetry-otlp-http-exporter` =
  (project in file("modules/opentelemetry-otlp-http-exporter"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-opentelemetry-otlp-http-exporter",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.circeGeneric,
        Dependencies.fs2,
        Dependencies.http4sClient,
        Dependencies.http4sEmberClient,
        (Dependencies.openTelemetryProto % "protobuf").intransitive(),
        Dependencies.scalapbJson
      ),
      PB.protoSources in Compile += target.value / "protobuf_external",
      PB.targets in Compile := Seq(scalapb.gen(grpc = false, lenses = false) -> (sourceManaged in Compile).value)
    )
    .dependsOn(model, kernel, `exporter-common`, `exporter-http`, `jaeger-integration-test` % "test->compile")

lazy val `stackdriver-common` =
  (project in file("modules/stackdriver-common"))
    .settings(publishSettings)
    .settings(name := "trace4cats-stackdriver-common")

lazy val `stackdriver-grpc-exporter` =
  (project in file("modules/stackdriver-grpc-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-stackdriver-grpc-exporter",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.fs2,
        Dependencies.googleCredentials,
        Dependencies.googleCloudTrace
      )
    )
    .dependsOn(model, kernel, `exporter-common`, `stackdriver-common`)

lazy val `stackdriver-http-exporter` =
  (project in file("modules/stackdriver-http-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-stackdriver-http-exporter",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.circeGeneric,
        Dependencies.circeParser,
        Dependencies.enumeratumCirce,
        Dependencies.fs2,
        Dependencies.http4sClient,
        Dependencies.http4sCirce,
        Dependencies.http4sEmberClient,
        Dependencies.jwt,
        Dependencies.log4cats
      )
    )
    .dependsOn(model, kernel, `exporter-common`, `exporter-http`, `stackdriver-common`)

lazy val `datadog-http-exporter` =
  (project in file("modules/datadog-http-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-datadog-http-exporter",
      libraryDependencies ++= Dependencies.test.map(_ % Test),
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.circeGeneric,
        Dependencies.circeParser,
        Dependencies.fs2,
        Dependencies.http4sClient,
        Dependencies.http4sCirce,
        Dependencies.http4sEmberClient,
        Dependencies.log4cats
      )
    )
    .dependsOn(model, kernel, `exporter-common`, `exporter-http`, test % "test->compile")

lazy val `exporter-common` =
  (project in file("modules/exporter-common"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-exporter-common",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.log4cats)
    )
    .dependsOn(model, kernel)

lazy val `exporter-http` =
  (project in file("modules/exporter-http"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-exporter-http",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.http4sClient)
    )
    .dependsOn(model, kernel)

lazy val `avro-exporter` =
  (project in file("modules/avro-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro-exporter",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.fs2Io)
    )
    .dependsOn(model, kernel, avro, `exporter-common`)

lazy val `avro-server` =
  (project in file("modules/avro-server"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro-server",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.fs2Io, Dependencies.log4cats)
    )
    .dependsOn(model, avro)

lazy val natchez = (project in file("modules/natchez"))
  .settings(publishSettings)
  .settings(name := "trace4cats-natchez", libraryDependencies ++= Seq(Dependencies.natchez))
  .dependsOn(model, kernel, core)

lazy val agent = (project in file("modules/agent"))
  .settings(noPublishSettings)
  .settings(graalSettings)
  .settings(
    name := "trace4cats-agent",
    libraryDependencies ++= Seq(
      Dependencies.catsEffect,
      Dependencies.declineEffect,
      Dependencies.log4cats,
      Dependencies.logback
    )
  )
  .dependsOn(model, `avro-exporter`, `avro-server`, `exporter-common`)
  .enablePlugins(GraalVMNativeImagePlugin)

lazy val `collector-common` = (project in file("modules/collector-common"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-collector-common",
    libraryDependencies ++= Seq(
      Dependencies.catsEffect,
      Dependencies.fs2,
      Dependencies.http4sJdkClient,
      Dependencies.log4cats
    )
  )
  .dependsOn(model, kernel)

lazy val collector = (project in file("modules/collector"))
  .settings(noPublishSettings)
  .settings(
    name := "trace4cats-collector",
    dockerRepository := Some("janstenpickle"),
    dockerUpdateLatest := true,
    dockerBaseImage := "openjdk:13",
    dockerExposedPorts += 7777,
    dockerExposedUdpPorts += 7777,
    daemonUserUid in Docker := Some("9000"),
    javaOptions in Universal ++= Seq("-Djava.net.preferIPv4Stack=true"),
    libraryDependencies ++= Seq(
      Dependencies.catsEffect,
      Dependencies.declineEffect,
      Dependencies.fs2,
      Dependencies.grpcOkHttp,
      Dependencies.log4cats,
      Dependencies.logback
    )
  )
  .dependsOn(
    model,
    `collector-common`,
    `exporter-common`,
    `avro-exporter`,
    `avro-server`,
    `datadog-http-exporter`,
    `jaeger-thrift-exporter`,
    `log-exporter`,
    `opentelemetry-jaeger-exporter`,
    `opentelemetry-otlp-grpc-exporter`,
    `opentelemetry-otlp-http-exporter`,
    `stackdriver-grpc-exporter`,
    `stackdriver-http-exporter`
  )
  .enablePlugins(UniversalPlugin, JavaAppPackaging, DockerPlugin)

lazy val `collector-lite` = (project in file("modules/collector-lite"))
  .settings(noPublishSettings)
  .settings(graalSettings)
  .settings(
    name := "trace4cats-collector-lite",
    libraryDependencies ++= Seq(
      Dependencies.catsEffect,
      Dependencies.declineEffect,
      Dependencies.fs2,
      Dependencies.log4cats,
      Dependencies.logback
    )
  )
  .dependsOn(
    model,
    `exporter-common`,
    `collector-common`,
    `avro-exporter`,
    `avro-server`,
    `datadog-http-exporter`,
    `jaeger-thrift-exporter`,
    `log-exporter`,
    `opentelemetry-otlp-http-exporter`,
    `stackdriver-http-exporter`
  )
  .enablePlugins(GraalVMNativeImagePlugin)
