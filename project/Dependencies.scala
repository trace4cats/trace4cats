import sbt._

object Dependencies {
  object Versions {
    val caseInsensitive = "1.4.0"
    val cats = "2.9.0"
    val catsEffect = "3.5.0"
    val collectionCompat = "2.11.0"
    val commonsCodec = "1.15"
    val fs2 = "3.7.0"
    val hotswapRef = "0.2.2"
    val kittens = "2.3.2"
    val log4cats = "2.6.0"
    val slf4j = "1.7.36"
    val scala212 = "2.12.16"
    val scala213 = "2.13.8"
    val scala3 = "3.3.0"

    val catsTestkitScalatest = "2.1.5"
    val disciplineScalatest = "2.2.0"
    val discipline = "1.5.1"
    val scalaCheck = "1.16.0"
    val scalaCheckShapeless = "1.3.0"
    val scalaTest = "3.2.16"

    val kindProjector = "0.13.2"
    val betterMonadicFor = "0.3.1"
  }

  lazy val caseInsensitive = "org.typelevel"           %% "case-insensitive"        % Versions.caseInsensitive
  lazy val cats = "org.typelevel"                      %% "cats-core"               % Versions.cats
  lazy val catsEffectStd = "org.typelevel"             %% "cats-effect-std"         % Versions.catsEffect
  lazy val catsEffect = "org.typelevel"                %% "cats-effect"             % Versions.catsEffect
  lazy val commonsCodec = "commons-codec"               % "commons-codec"           % Versions.commonsCodec
  lazy val collectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.collectionCompat
  lazy val fs2 = "co.fs2"                              %% "fs2-core"                % Versions.fs2
  lazy val fs2Io = "co.fs2"                            %% "fs2-io"                  % Versions.fs2
  lazy val hotswapRef = "io.janstenpickle"             %% "hotswap-ref"             % Versions.hotswapRef
  lazy val kittens = "org.typelevel"                   %% "kittens"                 % Versions.kittens
  lazy val log4cats = "org.typelevel"                  %% "log4cats-slf4j"          % Versions.log4cats
  lazy val slf4jNop = "org.slf4j"                       % "slf4j-nop"               % Versions.slf4j

  lazy val catsLaws = "org.typelevel"             %% "cats-laws"              % Versions.cats
  lazy val catsEffectLaws = "org.typelevel"       %% "cats-effect-laws"       % Versions.catsEffect
  lazy val catsEffectTestkit = "org.typelevel"    %% "cats-effect-testkit"    % Versions.catsEffect
  lazy val catsTestkitScalatest = "org.typelevel" %% "cats-testkit-scalatest" % Versions.catsTestkitScalatest
  lazy val disciplineScalatest = "org.typelevel"  %% "discipline-scalatest"   % Versions.disciplineScalatest
  lazy val disciplineCore = "org.typelevel"       %% "discipline-core"        % Versions.discipline
  lazy val scalacheck = "org.scalacheck"          %% "scalacheck"             % Versions.scalaCheck
  lazy val scalacheckShapeless =
    "com.github.alexarchambault"       %% "scalacheck-shapeless_1.15" % Versions.scalaCheckShapeless
  lazy val scalaTest = "org.scalatest" %% "scalatest"                 % Versions.scalaTest

  lazy val test =
    Seq(
      catsLaws,
      catsEffectLaws,
      catsEffectTestkit,
      catsTestkitScalatest,
      disciplineScalatest,
      disciplineCore,
      scalacheck,
      // scalacheckShapeless, // TODO re-add once compatible with Scala 3
      scalaTest
    )

  lazy val kindProjector = ("org.typelevel" % "kind-projector"     % Versions.kindProjector).cross(CrossVersion.full)
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
}
