import sbt._

object Dependencies {
  object Versions {
    val cats = "2.1.1"
    val catsEffect = "2.1.0"
    val collectionCompat = "2.1.6"
    val commonsCodec = "1.14"
    val decline = "1.2.0"
    val enumeratum = "1.6.1"
    val fs2 = "2.4.2"
    val grpcOkHttp = "1.30.2"
    val jaeger = "1.2.0"
    val log4cats = "1.1.1"
    val logback = "1.2.3"
    val natchez = "0.0.11"
    val openTelemetry = "0.5.0"
    val scala212 = "2.12.11"
    val scala213 = "2.13.3"
    val vulcan = "1.1.0"

    val disciplineScalatest = "1.0.1"
    val discipline = "1.0.2"
    val scalaCheck = "1.14.3"
    val scalaCheckShapeless = "1.2.5"
    val scalaTest = "3.2.0"

  }

  lazy val cats = "org.typelevel"                      %% "cats-core"                   % Versions.cats
  lazy val catsEffect = "org.typelevel"                %% "cats-effect"                 % Versions.catsEffect
  lazy val commonsCodec = "commons-codec"              % "commons-codec"                % Versions.commonsCodec
  lazy val collectionCompat = "org.scala-lang.modules" %% "scala-collection-compat"     % Versions.collectionCompat
  lazy val enumeratum = "com.beachape"                 %% "enumeratum"                  % Versions.enumeratum
  lazy val declineEffect = "com.monovore"              %% "decline-effect"              % Versions.decline
  lazy val fs2 = "co.fs2"                              %% "fs2-core"                    % Versions.fs2
  lazy val fs2Io = "co.fs2"                            %% "fs2-io"                      % Versions.fs2
  lazy val grpcOkHttp = "io.grpc"                      % "grpc-okhttp"                  % Versions.grpcOkHttp
  lazy val jaegerThrift = "io.jaegertracing"           % "jaeger-thrift"                % Versions.jaeger
  lazy val log4cats = "io.chrisdavenport"              %% "log4cats-slf4j"              % Versions.log4cats
  lazy val logback = "ch.qos.logback"                  % "logback-classic"              % Versions.logback
  lazy val natchez = "org.tpolecat"                    %% "natchez-core"                % Versions.natchez
  lazy val openTelemetryExporter = "io.opentelemetry"  % "opentelemetry-exporters-otlp" % Versions.openTelemetry
  lazy val vulcan = "com.github.fd4s"                  %% "vulcan"                      % Versions.vulcan
  lazy val vulcanGeneric = "com.github.fd4s"           %% "vulcan-generic"              % Versions.vulcan
  lazy val vulcanEnumeratum = "com.github.fd4s"        %% "vulcan-enumeratum"           % Versions.vulcan

  lazy val catsLaws = "org.typelevel"                         %% "cats-laws"                 % Versions.cats
  lazy val disciplineScalatest = "org.typelevel"              %% "discipline-scalatest"      % Versions.disciplineScalatest % Test
  lazy val disciplineCore = "org.typelevel"                   %% "discipline-core"           % Versions.discipline % Test
  lazy val scalacheck = "org.scalacheck"                      %% "scalacheck"                % Versions.scalaCheck % Test
  lazy val scalacheckShapeless = "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % Versions.scalaCheckShapeless % Test
  lazy val scalaTest = "org.scalatest"                        %% "scalatest"                 % Versions.scalaTest % Test

  lazy val test = Seq(catsLaws, disciplineScalatest, disciplineCore, scalacheck, scalacheckShapeless, scalaTest)
}
