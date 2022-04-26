lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(compilerPlugin(Dependencies.kindProjector), compilerPlugin(Dependencies.betterMonadicFor))
      case _ => Seq.empty
    }
  },
  scalacOptions += {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "-Wconf:any:wv"
      case _ => "-Wconf:any:v"
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
    `fp-utils`,
    `fp-utils-io`,
    `fp-utils-laws`,
    `dynamic-sampling`,
    `dynamic-sampling-config`,
    filtering,
    fs2,
    `inject-io`,
    kernel,
    meta,
    `rate-sampling`,
    `tail-sampling`,
    testkit
  )

lazy val testkit = (project in file("modules/testkit"))
  .settings(publishSettings)
  .settings(name := "trace4cats-testkit", libraryDependencies ++= Dependencies.test ++ Seq(Dependencies.fs2))
  .dependsOn(kernel)

lazy val kernel =
  (project in file("modules/kernel"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-kernel",
      libraryDependencies ++= Dependencies.test.map(_ % Test),
      buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, sbtVersion),
      buildInfoPackage := "io.janstenpickle.trace4cats.kernel",
      Test / unmanagedSourceDirectories ++= Seq(
        baseDirectory.value / ".." / "testkit" / "src" / "main" / "scala",
        baseDirectory.value / ".." / "core" / "src" / "main" / "scala"
      ),
      libraryDependencies ++= Seq(
        Dependencies.catsEffectStd,
        Dependencies.commonsCodec,
        // Dependencies.kittens, // TODO re-add once compatible with Scala 3
        Dependencies.caseInsensitive,
        Dependencies.collectionCompat,
      ),
      libraryDependencies ++= (Dependencies.test ++ Seq(
        Dependencies.fs2,
        Dependencies.log4cats,
        Dependencies.hotswapRef
      )).map(_ % Test)
    )
    .dependsOn(`fp-utils` % Test)
    .enablePlugins(BuildInfoPlugin)

lazy val core =
  (project in file("modules/core"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-core",
      libraryDependencies ++= Seq(
        Dependencies.collectionCompat,
        Dependencies.fs2,
        Dependencies.log4cats,
        Dependencies.hotswapRef
      ),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(kernel, `fp-utils`, testkit % Test)

lazy val `fp-utils` =
  (project in file("modules/fp-utils"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-fp-utils",
      libraryDependencies ++= Seq(Dependencies.cats),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )

lazy val `fp-utils-laws` =
  (project in file("modules/fp-utils-laws"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-fp-utils-laws",
      libraryDependencies ++= Seq(Dependencies.catsLaws),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )
    .dependsOn(`fp-utils`)

lazy val meta =
  (project in file("modules/meta"))
    .settings(publishSettings)
    .settings(
      name := "trace4cats-meta",
      libraryDependencies ++= Seq(Dependencies.log4cats),
      libraryDependencies ++= Seq(Dependencies.slf4jNop).map(_ % Test)
    )
    .dependsOn(kernel, core, testkit % Test)

lazy val inject = (project in file("modules/inject"))
  .settings(publishSettings)
  .settings(name := "trace4cats-inject", libraryDependencies ++= Seq(Dependencies.catsEffect).map(_ % Test))
  .dependsOn(core, `fp-utils`)

lazy val `fp-utils-io` =
  (project in file("modules/fp-utils-io"))
    .settings(publishSettings)
    .settings(name := "trace4cats-fp-utils-io", libraryDependencies ++= Seq(Dependencies.catsEffect))
    .dependsOn(`fp-utils`, `fp-utils-laws` % "compile->compile;test->test", testkit % Test)

lazy val `inject-io` = (project in file("modules/inject-io"))
  .settings(publishSettings)
  .settings(name := "trace4cats-inject-io")
  .dependsOn(`fp-utils-io`, core)

lazy val fs2 = (project in file("modules/fs2"))
  .settings(publishSettings)
  .settings(
    name := "trace4cats-fs2",
    libraryDependencies ++= Seq(Dependencies.fs2),
    libraryDependencies ++= Dependencies.test.map(_ % Test)
  )
  .dependsOn(inject, core % Test, testkit % Test)

lazy val filtering = (project in file("modules/filtering"))
  .settings(publishSettings)
  .settings(name := "trace4cats-filtering", libraryDependencies ++= Dependencies.test.map(_ % Test))
  .dependsOn(core)

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
  .dependsOn(core)
