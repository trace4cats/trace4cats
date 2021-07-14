package io.janstenpickle.trace4cats.`export`

import cats.Applicative
import cats.effect.{Sync, Timer}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
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

  def apply[F[_]: Sync: Timer, G[_], A](client: Client[F], uri: String, makePayload: Batch[G] => A)(implicit
    encoder: EntityEncoder[F, A]
  ): F[SpanExporter[F, G]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      Applicative[F].pure(List.empty[Header.ToRaw]),
      Method.POST,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, G[_], A](
    client: Client[F],
    uri: String,
    makePayload: Batch[G] => A,
    staticHeaders: List[Header.ToRaw]
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F, G]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      Applicative[F].pure(List.empty[Header.ToRaw]),
      Method.POST,
      staticHeaders
    )

  def apply[F[_]: Sync: Timer, G[_], A](
    client: Client[F],
    uri: String,
    makePayload: Batch[G] => A,
    updatedUri: Uri => F[Uri]
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F, G]] =
    apply(
      client,
      uri,
      makePayload,
      updatedUri,
      Applicative[F].pure(List.empty[Header.ToRaw]),
      Method.POST,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, G[_], A](client: Client[F], uri: String, makePayload: Batch[G] => A, method: Method)(
    implicit encoder: EntityEncoder[F, A]
  ): F[SpanExporter[F, G]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      Applicative[F].pure(List.empty[Header.ToRaw]),
      method,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, G[_], A](
    client: Client[F],
    uri: String,
    makePayload: Batch[G] => A,
    dynamicHeaders: F[List[Header.ToRaw]]
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F, G]] =
    apply(
      client,
      uri,
      makePayload,
      Applicative[F].pure(_),
      dynamicHeaders,
      Method.POST,
      List(`Content-Type`(MediaType.application.json))
    )

  def apply[F[_]: Sync: Timer, G[_], A](
    client: Client[F],
    uri: String,
    makePayload: Batch[G] => A,
    updatedUri: Uri => F[Uri],
    dynamicHeaders: F[List[Header.ToRaw]],
    method: Method,
    staticHeaders: List[Header.ToRaw]
  )(implicit encoder: EntityEncoder[F, A]): F[SpanExporter[F, G]] =
    Uri.fromString(uri).liftTo[F].map { parsedUri =>
      new SpanExporter[F, G] with Http4sClientDsl[F] {
        override def exportBatch(batch: Batch[G]): F[Unit] =
          for {
            u <- updatedUri(parsedUri)
            dynHeaders <- dynamicHeaders
            req = method(makePayload(batch), u, staticHeaders ++ dynHeaders: _*)
            _ <-
              Stream
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
