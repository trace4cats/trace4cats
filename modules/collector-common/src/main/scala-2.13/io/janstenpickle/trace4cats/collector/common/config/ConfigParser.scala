package io.janstenpickle.trace4cats.collector.common.config

import java.nio.file.{Files, NoSuchFileException, Paths}

import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import io.circe.parser.decodeAccumulating
import io.circe.{yaml, Decoder, Error}

import scala.util.control.NoStackTrace

object ConfigParser {
  case class ConfigParseError(file: String, errors: NonEmptyList[Error]) extends RuntimeException with NoStackTrace {
    override def getMessage: String =
      s"Failed to parse config file $file:\n\t" + errors.map(_.getMessage).mkString_("\n\t")
  }

  case class FileNotFound(file: String) extends RuntimeException with NoStackTrace {
    override def getMessage: String = s"Config file not found: $file"
  }

  private def loadFile[F[_]: Sync](file: String): F[String] =
    Sync[F].adaptError(Sync[F].blocking(Files.readString(Paths.get(file)))) { case _: NoSuchFileException =>
      FileNotFound(file)
    }

  def parseJson[F[_]: Sync, A: Decoder](file: String): F[A] =
    for {
      string <- loadFile(file)
      a <- decodeAccumulating(string).leftMap(ConfigParseError(file, _)).toEither.liftTo[F]
    } yield a

  def parseYaml[F[_]: Sync, A: Decoder](file: String): F[A] =
    for {
      string <- loadFile(file)
      json <- yaml.parser.parse(string).liftTo[F]
      a <- Decoder[A].decodeAccumulating(json.hcursor).leftMap(ConfigParseError(file, _)).toEither.liftTo[F]
    } yield a

  def parse[F[_]: Sync, A: Decoder](file: String): F[A] =
    if (file.endsWith(".json")) parseJson(file)
    else parseYaml(file)
}
