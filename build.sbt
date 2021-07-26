lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(compilerPlugin(Dependencies.kindProjector), compilerPlugin(Dependencies.betterMonadicFor))
      case _ => Seq.empty
    }
  },
  scalacOptions := {
    val opts = scalacOptions.value
    val wconf = "-Wconf:any:wv"
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => opts :+ wconf
      case _ => opts
    }
  },
  Test / fork := true,
  resolvers += Resolver.sonatypeRepo("releases"),
  autoAPIMappings := true,
  apiURL := Some(url(s"https://trace4cats.github.io/api/${version.value}")),
)

lazy val noPublishSettings =
  commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None, publish / skip := true)

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
    `exporter-common`,
    `exporter-stream`,
    filtering,
    fs2,
    inject,
    kernel,
    `log-exporter`,
    meta,
    model,
    `rate-sampling`,
    `tail-sampling`,
    testkit
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
    .settings(
      name := "trace4cats-core",
      libraryDependencies ++= Seq(Dependencies.collectionCompat),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(model, kernel, testkit % "test->compile", `exporter-common` % "test->compile")

lazy val base =
  (project in file("modules/base"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-base",
      libraryDependencies ++= Seq(Dependencies.cats),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )

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
    .settings(
      name := "trace4cats-meta",
      libraryDependencies ++= Seq(Dependencies.log4cats),
      libraryDependencies ++= Seq(Dependencies.slf4jNop).map(_ % Test)
    )
    .dependsOn(model, kernel, core, `exporter-stream`, `exporter-common` % "test->compile", testkit % "test->compile")

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
