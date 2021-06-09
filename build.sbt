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
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.0").cross(CrossVersion.patch)),
  libraryDependencies ++= Seq(Dependencies.cats, Dependencies.collectionCompat),
  scalacOptions := {
    val opts = scalacOptions.value :+ "-Wconf:src=src_managed/.*:s,any:wv"

    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => opts.filterNot(Set("-Xfatal-warnings"))
      case _ => opts
    }
  },
  Test / fork := true,
  bintrayRepository := "trace4cats",
  Global / releaseEarlyWith := SonatypePublisher,
  credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
  releaseEarlyEnableSyncToMaven := true,
  pgpPublicRing := file("./.github/git adlocal.pubring.asc"),
  pgpSecretRing := file("./.github/local.secring.asc"),
  crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala212),
  resolvers += Resolver.sonatypeRepo("releases"),
  ThisBuild / evictionErrorLevel := Level.Warn
)

lazy val noPublishSettings = commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats")
  .aggregate(
    base,
    `base-laws`,
    core,
    `dynamic-sampling`,
    `dynamic-sampling-config`,
    example,
    `exporter-common`,
    `exporter-http`,
    `exporter-stream`,
    filtering,
    fs2,
    `graal-kafka`,
    inject,
    `jaeger-integration-test`,
    kernel,
    `log-exporter`,
    meta,
    model,
    natchez,
    `rate-sampling`,
    `tail-sampling`,
    `tail-sampling-cache-store`,
    `tail-sampling-redis-store`,
    testkit,
  )

lazy val model =
  (project in file("modules/model"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-model",
      libraryDependencies ++= Dependencies.test.map(_ % Test),
      libraryDependencies ++= Seq(
        Dependencies.catsEffectKernel,
        Dependencies.commonsCodec,
        Dependencies.kittens,
        Dependencies.caseInsensitive
      )
    )

lazy val example = (project in file("modules/example"))
  .settings(noPublishSettings)
  .settings(name := "trace4cats-example", libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.logback))
  .dependsOn(
    model,
    kernel,
    core,
    inject,
    fs2,
    natchez,
    `log-exporter`,
    `tail-sampling`,
    `tail-sampling-cache-store`,
    filtering,
    `rate-sampling`,
    meta,
  )

lazy val testkit = (project in file("modules/testkit"))
  .settings(publishSettings)
  .settings(name := "trace4cats-testkit", libraryDependencies ++= Dependencies.test ++ Seq(Dependencies.fs2))
  .dependsOn(model)

lazy val kernel =
  (project in file("modules/kernel"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-kernel",
      libraryDependencies ++= Dependencies.test.map(_ % Test),
      buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, sbtVersion),
      buildInfoPackage := "io.janstenpickle.trace4cats.kernel"
    )
    .dependsOn(model, testkit % "test->compile")
    .enablePlugins(BuildInfoPlugin)

lazy val core =
  (project in file("modules/core"))
    .settings(publishSettings)
    .settings(name := "trace4cats-core", libraryDependencies ++= Dependencies.test.map(_ % Test))
    .dependsOn(model, kernel, testkit % "test->compile", `exporter-common` % "test->compile")

lazy val base =
  (project in file("modules/base"))
    .settings(publishSettings)
    .settings(name := "trace4cats-base", libraryDependencies ++= Dependencies.test.map(_ % Test))

lazy val `base-laws` =
  (project in file("modules/base-laws"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-base-laws",
      libraryDependencies ++= Seq(Dependencies.catsLaws),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(base)

lazy val `log-exporter` =
  (project in file("modules/log-exporter"))
    .settings(publishSettings)
    .settings(name := "trace4cats-log-exporter", libraryDependencies ++= Seq(Dependencies.log4cats))
    .dependsOn(model, kernel)

lazy val `jaeger-integration-test` =
  (project in file("modules/jaeger-integration-test"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-jaeger-integration-test",
      libraryDependencies ++= Dependencies.test,
      libraryDependencies ++= Seq(
        Dependencies.circeGeneric,
        Dependencies.http4sCirce,
        Dependencies.http4sBlazeClient,
        Dependencies.logback
      )
    )
    .dependsOn(kernel, testkit)

lazy val `exporter-stream` =
  (project in file("modules/exporter-stream"))
    .settings(publishSettings)
    .settings(name := "trace4cats-exporter-stream", libraryDependencies ++= Seq(Dependencies.fs2))
    .dependsOn(model, kernel)

lazy val `exporter-common` =
  (project in file("modules/exporter-common"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-exporter-common",
      libraryDependencies ++= Seq(Dependencies.kittens, Dependencies.log4cats, Dependencies.hotswapRef),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(model, kernel, `exporter-stream`, testkit % "test->compile")

lazy val meta =
  (project in file("modules/meta"))
    .settings(publishSettings)
    .settings(name := "trace4cats-meta", libraryDependencies ++= Seq(Dependencies.log4cats))
    .dependsOn(model, kernel, core, `exporter-stream`, `exporter-common` % "test->compile", testkit % "test->compile")

lazy val `exporter-http` =
  (project in file("modules/exporter-http"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-exporter-http",
      libraryDependencies ++= Seq(Dependencies.fs2, Dependencies.http4sClient)
    )
    .dependsOn(model, kernel)

lazy val inject = (project in file("modules/inject"))
  .settings(publishSettings)
  .settings(name := "trace4cats-inject", libraryDependencies ++= Seq(Dependencies.catsEffect).map(_ % Test))
  .dependsOn(model, kernel, core, base)

lazy val fs2 = (project in file("modules/fs2"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-fs2",
    libraryDependencies ++= Seq(Dependencies.fs2),
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(model, kernel, core, inject, `exporter-common` % "test->compile", testkit % "test->compile")

lazy val natchez = (project in file("modules/natchez"))
  .settings(publishSettings)
  .settings(name := "trace4cats-natchez", libraryDependencies ++= Seq(Dependencies.natchez))
  .dependsOn(model, kernel, core, inject)

lazy val `graal-kafka` = (project in file("modules/graal-kafka"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-graal-kafka",
    libraryDependencies ++= Seq(Dependencies.svm, Dependencies.kafka, Dependencies.micronautCore)
  )

lazy val filtering = (project in file("modules/filtering"))
  .settings(publishSettings)
  .settings(name := "trace4cats-filtering", libraryDependencies ++= Dependencies.test.map(_ % Test))
  .dependsOn(model, kernel, `exporter-stream`)

lazy val `dynamic-sampling` = (project in file("modules/dynamic-sampling"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-dynamic-sampling",
    libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.hotswapRef),
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(model, kernel, testkit % "test->compile")

lazy val `dynamic-sampling-config` = (project in file("modules/dynamic-sampling-config"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-dynamic-sampling-config",
    libraryDependencies ++= Seq(Dependencies.kittens),
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(model, kernel, `dynamic-sampling`, `rate-sampling`, testkit % "test->compile")

lazy val `rate-sampling` = (project in file("modules/rate-sampling"))
  .settings(publishSettings)
  .settings(name := "trace4cats-rate-sampling", libraryDependencies ++= Dependencies.test.map(_ % Test))
  .dependsOn(model, kernel, `tail-sampling`)

lazy val `tail-sampling` = (project in file("modules/tail-sampling"))
  .settings(publishSettings)
  .settings(name := "trace4cats-tail-sampling", libraryDependencies ++= Seq(Dependencies.log4cats))
  .dependsOn(model, kernel, `exporter-stream`)

lazy val `tail-sampling-cache-store` = (project in file("modules/tail-sampling-cache-store"))
  .settings(publishSettings)
  .settings(name := "trace4cats-tail-sampling-cache-store", libraryDependencies ++= Seq(Dependencies.scaffeine))
  .dependsOn(`tail-sampling`)

lazy val `tail-sampling-redis-store` = (project in file("modules/tail-sampling-redis-store"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-tail-sampling-redis-store",
    libraryDependencies ++= Seq(Dependencies.redis4cats, Dependencies.redis4catsLog4cats, Dependencies.scaffeine),
    libraryDependencies ++= (Dependencies.test :+ Dependencies.embeddedRedis).map(_ % Test)
  )
  .dependsOn(`tail-sampling`, testkit % "test->compile")

addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
