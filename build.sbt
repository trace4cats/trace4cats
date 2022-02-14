lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(compilerPlugin(Dependencies.kindProjector), compilerPlugin(Dependencies.betterMonadicFor))
      case _ => Seq.empty
    }
  },
  scalacOptions += "-Wconf:any:wv",
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
    `kernel-tests`,
    `log-exporter`,
    meta,
    `rate-sampling`,
    `tail-sampling`,
    testkit
  )

lazy val kernel =
  (project in file("modules/kernel"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-kernel",
      libraryDependencies ++=
        Seq(
          Dependencies.catsEffectKernel,
          Dependencies.commonsCodec,
          // Dependencies.kittens, // TODO re-add once compatible with Scala 3
          Dependencies.caseInsensitive
        ) ++
          Dependencies.test.map(_ % Test),
      buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, sbtVersion),
      buildInfoPackage := "io.janstenpickle.trace4cats.kernel"
    )
    .enablePlugins(BuildInfoPlugin)

lazy val testkit = (project in file("modules/testkit"))
  .settings(publishSettings)
  .settings(name := "trace4cats-testkit", libraryDependencies ++= Dependencies.test ++ Seq(Dependencies.fs2))
  .dependsOn(kernel)

lazy val `kernel-tests` =
  (project in file("modules/kernel-tests"))
    .settings(noPublishSettings)
    .settings(name := "trace4cats-kernel-testkit")
    .dependsOn(testkit)
    .enablePlugins(BuildInfoPlugin)

lazy val core =
  (project in file("modules/core"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-core",
      libraryDependencies ++= Seq(Dependencies.collectionCompat),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(kernel, testkit % Test, `exporter-common`)

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
    .dependsOn(inject)

lazy val `log-exporter` =
  (project in file("modules/log-exporter"))
    .settings(publishSettings)
    .settings(name := "trace4cats-log-exporter", libraryDependencies ++= Seq(Dependencies.log4cats))
    .dependsOn(kernel)

lazy val `exporter-stream` =
  (project in file("modules/exporter-stream"))
    .settings(publishSettings)
    .settings(name := "trace4cats-exporter-stream", libraryDependencies ++= Seq(Dependencies.fs2))
    .dependsOn(kernel)

lazy val `exporter-common` =
  (project in file("modules/exporter-common"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-exporter-common",
      // TODO re-add kittens once compatible with Scala 3
      libraryDependencies ++= Seq( /*Dependencies.kittens,*/ Dependencies.log4cats, Dependencies.hotswapRef),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(kernel, `exporter-stream`, testkit % Test)

lazy val meta =
  (project in file("modules/meta"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-meta",
      libraryDependencies ++= Seq(Dependencies.log4cats),
      libraryDependencies ++= Seq(Dependencies.slf4jNop).map(_ % Test)
    )
    .dependsOn(kernel, core, `exporter-stream`, `exporter-common`, testkit % Test)

lazy val inject = (project in file("modules/inject"))
  .settings(publishSettings)
  .settings(name := "trace4cats-inject", libraryDependencies ++= Seq(Dependencies.catsEffect).map(_ % Test))
  .dependsOn(core, base)

lazy val fs2 = (project in file("modules/fs2"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-fs2",
    libraryDependencies ++= Seq(Dependencies.fs2),
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(inject, `exporter-common`, testkit % Test)

lazy val filtering = (project in file("modules/filtering"))
  .settings(publishSettings)
  .settings(name := "trace4cats-filtering", libraryDependencies ++= Dependencies.test.map(_ % Test))
  .dependsOn(kernel, `exporter-stream`)

lazy val `dynamic-sampling` = (project in file("modules/dynamic-sampling"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-dynamic-sampling",
    libraryDependencies ++= Seq(Dependencies.catsEffect, Dependencies.fs2, Dependencies.hotswapRef),
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(kernel, testkit % Test)

lazy val `dynamic-sampling-config` = (project in file("modules/dynamic-sampling-config"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-dynamic-sampling-config",
    // libraryDependencies ++= Seq(Dependencies.kittens), // TODO re-add once compatible with Scala 3
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(kernel, `dynamic-sampling`, `rate-sampling`, testkit % Test)

lazy val `rate-sampling` = (project in file("modules/rate-sampling"))
  .settings(publishSettings)
  .settings(name := "trace4cats-rate-sampling", libraryDependencies ++= Dependencies.test.map(_ % Test))
  .dependsOn(kernel, `tail-sampling`)

lazy val `tail-sampling` = (project in file("modules/tail-sampling"))
  .settings(publishSettings)
  .settings(name := "trace4cats-tail-sampling", libraryDependencies ++= Seq(Dependencies.log4cats))
  .dependsOn(kernel, `exporter-stream`)
