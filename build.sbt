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
    `buffering-exporter`,
    `collector-common`,
    `completer-common`,
    `log-exporter`,
    `jaeger-thrift-exporter`,
    `opentelemetry-exporter`,
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
  .dependsOn(model, kernel, `avro-exporter`, core)

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

lazy val `jaeger-thrift-exporter` =
  (project in file("modules/jaeger-thrift-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-thrift-exporter",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.jaegerThrift)
    )
    .dependsOn(model, kernel, `completer-common`)

lazy val `opentelemetry-exporter` =
  (project in file("modules/opentelemetry-exporter"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-opentelemetry-exporter",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.openTelemetryExporter)
    )
    .dependsOn(model, kernel, `completer-common`)

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
    .dependsOn(model, kernel, `completer-common`, `stackdriver-common`)

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
        Dependencies.http4Client,
        Dependencies.http4Circe,
        Dependencies.httpEmberClient,
        Dependencies.jwt,
        Dependencies.log4cats,
        Dependencies.logback
      )
    )
    .dependsOn(model, kernel, `completer-common`, `stackdriver-common`)

lazy val `completer-common` =
  (project in file("modules/completer-common"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-completer-common",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.log4cats)
    )
    .dependsOn(model, kernel)

lazy val `avro-exporter` =
  (project in file("modules/avro-exporter"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro-exporter",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.fs2Io)
    )
    .dependsOn(model, kernel, avro, `completer-common`)

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
  .dependsOn(model, `avro-exporter`, `avro-server`, `buffering-exporter`)
  .enablePlugins(GraalVMNativeImagePlugin)

lazy val `buffering-exporter` = (project in file("modules/buffering-exporter"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-buffering-exporter",
    libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.log4cats)
  )
  .dependsOn(model, kernel)

lazy val `collector-common` = (project in file("modules/collector-common"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-collector-common",
    libraryDependencies ++= Seq(
      Dependencies.catsEffect,
      Dependencies.declineEffect,
      Dependencies.fs2,
      Dependencies.http4JdkClient,
      Dependencies.log4cats
    )
  )
  .dependsOn(model, kernel, `buffering-exporter`)

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
    `avro-exporter`,
    `avro-server`,
    `jaeger-thrift-exporter`,
    `log-exporter`,
    `opentelemetry-exporter`,
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
    `collector-common`,
    `avro-exporter`,
    `avro-server`,
    `jaeger-thrift-exporter`,
    `log-exporter`,
    `stackdriver-http-exporter`
  )
  .enablePlugins(GraalVMNativeImagePlugin)
