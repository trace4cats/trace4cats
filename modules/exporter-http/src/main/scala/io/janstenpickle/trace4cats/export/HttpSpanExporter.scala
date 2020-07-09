package io.janstenpickle.trace4cats.`export`

import cats.Applicative
import cats.effect.{Sync, Timer}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Method.PermitsBody
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

object HttpSpanExporter {
  case class UnexpectedResponse(status: Status, message: String) extends RuntimeException with NoStackTrace {
    override def getMessage: String =
      s"""HTTP Status: ${status.toString()}
         |
         |$message
         |""".stripMargin
  }

  def apply[F[_]: Sync: Timer, A](client: Client[F], uri: String, makePayload: Batch => A)(
    implicit encoder: EntityEncoder[F, A]
  ): F[SpanExporter[F]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      Applicative[F].pure(List.empty[Header]),
      Method.POST,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, A](client: Client[F], uri: String, makePayload: Batch => A, staticHeaders: List[Header])(
    implicit encoder: EntityEncoder[F, A]
  ): F[SpanExporter[F]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      Applicative[F].pure(List.empty[Header]),
      Method.POST,
      staticHeaders
    )

  def apply[F[_]: Sync: Timer, A](client: Client[F], uri: String, makePayload: Batch => A, updatedUri: Uri => F[Uri])(
    implicit encoder: EntityEncoder[F, A]
  ): F[SpanExporter[F]] =
    apply(
      client,
      uri,
      makePayload,
      updatedUri,
      Applicative[F].pure(List.empty[Header]),
      Method.POST,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, A](
    client: Client[F],
    uri: String,
    makePayload: Batch => A,
    method: Method with PermitsBody
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      Applicative[F].pure(List.empty[Header]),
      method,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, A](
    client: Client[F],
    uri: String,
    makePayload: Batch => A,
    dynamicHeaders: F[List[Header]]
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      dynamicHeaders,
      Method.POST,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, A](
    client: Client[F],
    uri: String,
    makePayload: Batch => A,
    updatedUri: Uri => F[Uri],
    dynamicHeaders: F[List[Header]],
    method: Method with PermitsBody,
    staticHeaders: List[Header]
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F]] = Uri.fromString(uri).liftTo[F].map { parsedUri =>
    new SpanExporter[F] with Http4sClientDsl[F] {
      override def exportBatch(batch: Batch): F[Unit] =
        for {
          u <- updatedUri(parsedUri)
          dynHeaders <- dynamicHeaders
          req <- method(makePayload(batch), u, staticHeaders ++ dynHeaders: _*)
          _ = println(req)
          _ <- Stream
            .retry(
              client.expectOr[String](req)(resp => resp.as[String].map(UnexpectedResponse(resp.status, _))),
              10.millis,
              _ + 5.millis,
              2
            )
            .compile
            .drain
        } yield ()
    }
  }
}
