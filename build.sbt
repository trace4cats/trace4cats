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
  releaseEarlyWith := BintrayPublisher
)

lazy val noPublishSettings = commonSettings ++ Seq(
  crossScalaVersions := Seq(Dependencies.Versions.scala213),
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publishTo := None
)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala212),
  publishArtifact in Test := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats")
  .aggregate(
    model,
    core,
    kernel,
    avro,
    `log-completer`,
    `jaeger-thrift-completer`,
    `stackdriver-completer`,
    `opentelemetry-completer`,
    `avro-completer`,
    `avro-server`,
    natchez
  )

lazy val model =
  (project in file("modules/model"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-model",
      libraryDependencies ++= Seq(Dependencies.enumeratum, Dependencies.commonsCodec)
    )

lazy val kernel =
  (project in file("modules/kernel"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-kernel",
      libraryDependencies ++= Dependencies.test,
      libraryDependencies ++= Seq(Dependencies.catsEffect % Test)
    )
    .dependsOn(model)

lazy val core =
  (project in file("modules/core"))
    .settings(publishSettings)
    .settings(name := "trace4cats-core", libraryDependencies ++= Seq(Dependencies.catsEffect))
    .dependsOn(model, kernel)

lazy val avro =
  (project in file("modules/avro"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro",
      libraryDependencies ++= Seq(Dependencies.vulcan, Dependencies.vulcanGeneric, Dependencies.vulcanEnumeratum)
    )
    .dependsOn(model)

lazy val `log-completer` =
  (project in file("modules/log-completer"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-log-completer",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.log4cats)
    )
    .dependsOn(model, kernel)

lazy val `jaeger-thrift-completer` =
  (project in file("modules/jaeger-thrift-completer"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-thrift-completer",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.jaegerThrift)
    )
    .dependsOn(model, kernel)

lazy val `opentelemetry-completer` =
  (project in file("modules/opentelemetry-completer"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-opentelemetry-completer",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.openTelemetryExporter)
    )
    .dependsOn(model, kernel)

lazy val `stackdriver-completer` =
  (project in file("modules/stackdriver-completer"))
    .settings(publishSettings)
    .settings(commonSettings)
    .settings(
      name := "trace4cats-stackdriver-completer",
      libraryDependencies ++= Seq(
        Dependencies.catsEffect,
        Dependencies.fs2,
        Dependencies.googleCredentials,
        Dependencies.googleCloudTrace
      )
    )
    .dependsOn(model, kernel)

lazy val `avro-completer` =
  (project in file("modules/avro-completer"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro-completer",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.fs2Io)
    )
    .dependsOn(model, kernel, avro)

lazy val `avro-server` =
  (project in file("modules/avro-server"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-avro-server",
      libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.fs2Io)
    )
    .dependsOn(model, avro)

lazy val natchez = (project in file("modules/natchez"))
  .settings(publishSettings)
  .settings(name := "trace4cats-natchez", libraryDependencies ++= Seq(Dependencies.natchez))
  .dependsOn(model, kernel, core)

lazy val agent = (project in file("modules/agent"))
  .settings(noPublishSettings)
  .settings(
    name := "trace4cats-agent",
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
    ),
    libraryDependencies ++= Seq(Dependencies.declineEffect, Dependencies.log4cats, Dependencies.logback)
  )
  .dependsOn(model, `avro-completer`, `avro-server`)
  .enablePlugins(GraalVMNativeImagePlugin)

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
    `avro-completer`,
    `avro-server`,
    `jaeger-thrift-completer`,
    `log-completer`,
    `opentelemetry-completer`,
    `stackdriver-completer`
  )
  .enablePlugins(UniversalPlugin, JavaAppPackaging, DockerPlugin)
