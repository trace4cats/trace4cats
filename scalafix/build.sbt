import sbt.Keys.libraryDependencies

lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val rulesCrossVersions = Seq(V.scala213, V.scala212)
lazy val scala3Version = "3.1.2"

inThisBuild(
  List(
    organization := "trace4cats",
    homepage := Some(url("https://github.com/trace4cats/trace4cats")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        "janstenpickle",
        "Chris Jansen",
        "janstenpickle@users.noreply.github.com",
        url = url("https://github.com/janstepickle")
      ),
      Developer(
        "catostrophe",
        "λoλcat",
        "catostrophe@users.noreply.github.com",
        url = url("https://github.com/catostrophe")
      )
    ),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val `trace4cats` = (project in file("."))
  .aggregate(
    rules.projectRefs ++
      input.projectRefs ++
      output.projectRefs ++
      tests.projectRefs: _*
  )
  .settings(publish / skip := true)

lazy val rules = projectMatrix
  .settings(moduleName := "scalafix", libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion)
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(rulesCrossVersions)

lazy val input = projectMatrix
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "io.janstenpickle" %% "trace4cats-kernel",
      "io.janstenpickle" %% "trace4cats-core",
      "io.janstenpickle" %% "trace4cats-filtering",
      "io.janstenpickle" %% "trace4cats-testkit",
      "io.janstenpickle" %% "trace4cats-meta",
      "io.janstenpickle" %% "trace4cats-dynamic-sampling",
      "io.janstenpickle" %% "trace4cats-dynamic-sampling-config",
      "io.janstenpickle" %% "trace4cats-exporter-common",
      "io.janstenpickle" %% "trace4cats-inject",
      "io.janstenpickle" %% "trace4cats-inject-zio",
      "io.janstenpickle" %% "trace4cats-rate-sampling",
      "io.janstenpickle" %% "trace4cats-fs2",
      "io.janstenpickle" %% "trace4cats-http4s-client",
      "io.janstenpickle" %% "trace4cats-http4s-server",
      "io.janstenpickle" %% "trace4cats-sttp-client3",
      "io.janstenpickle" %% "trace4cats-sttp-tapir",
      "io.janstenpickle" %% "trace4cats-natchez",
      "io.janstenpickle" %% "trace4cats-avro-exporter",
      "io.janstenpickle" %% "trace4cats-avro-kafka-exporter",
      "io.janstenpickle" %% "trace4cats-avro-kafka-consumer",
      "io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter",
      "io.janstenpickle" %% "trace4cats-log-exporter",
      "io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter",
      "io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter",
      "io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter",
      "io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter",
      "io.janstenpickle" %% "trace4cats-stackdriver-http-exporter",
      "io.janstenpickle" %% "trace4cats-datadog-http-exporter",
      "io.janstenpickle" %% "trace4cats-newrelic-http-exporter",
      "io.janstenpickle" %% "trace4cats-zipkin-http-exporter",
    ).map(_ % "0.13.1")
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = rulesCrossVersions :+ scala3Version)

lazy val output = projectMatrix
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "io.janstenpickle" %% "trace4cats-kernel",
      "io.janstenpickle" %% "trace4cats-core",
      "io.janstenpickle" %% "trace4cats-context-utils",
      "io.janstenpickle" %% "trace4cats-context-utils-laws",
      "io.janstenpickle" %% "trace4cats-tail-sampling",
      "io.janstenpickle" %% "trace4cats-fs2",
      "io.janstenpickle" %% "trace4cats-meta",
      "io.janstenpickle" %% "trace4cats-testkit",
    ).map(_ % "0.13.1-SNAPSHOT")
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = rulesCrossVersions :+ scala3Version)

lazy val testsAggregate = Project("tests", file("target/testsAggregate"))
  .aggregate(tests.projectRefs: _*)
  .settings(publish / skip := true)

lazy val tests = projectMatrix
  .settings(
    publish / skip := true,
    scalafixTestkitOutputSourceDirectories :=
      TargetAxis
        .resolve(output, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputSourceDirectories :=
      TargetAxis
        .resolve(input, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputClasspath :=
      TargetAxis.resolve(input, Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions :=
      TargetAxis.resolve(input, Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion :=
      TargetAxis.resolve(input, Compile / scalaVersion).value
  )
  .defaultAxes(rulesCrossVersions.map(VirtualAxis.scalaABIVersion) :+ VirtualAxis.jvm: _*)
  .jvmPlatform(scalaVersions = Seq(V.scala212), axisValues = Seq(TargetAxis(scala3Version)), settings = Seq())
  .jvmPlatform(scalaVersions = Seq(V.scala213), axisValues = Seq(TargetAxis(V.scala213)), settings = Seq())
  .jvmPlatform(scalaVersions = Seq(V.scala212), axisValues = Seq(TargetAxis(V.scala212)), settings = Seq())
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)

addCommandAlias("ci", ";clean ;test")
