package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.ApplicativeError
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Method.POST
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Status, Uri}

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

object OpenTelemetryOtlpHttpSpanExporter {
  case class UnexpectedResponse(status: Status, message: String) extends RuntimeException with NoStackTrace {
    override def getMessage: String =
      s"""HTTP Status: ${status.toString()}
         |
         |$message
         |""".stripMargin
  }

  def emberClient[F[_]: Concurrent: Timer: ContextShift: Logger](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 55681
  ): Resource[F, SpanExporter[F]] =
    EmberClientBuilder
      .default[F]
      .withLogger(Logger[F])
      .withBlocker(blocker)
      .build
      .evalMap(apply[F](_, host, port))

  def apply[F[_]: Sync: Timer](client: Client[F], host: String = "localhost", port: Int = 55681): F[SpanExporter[F]] =
    ApplicativeError[F, Throwable].fromEither(Uri.fromString(s"http://$host:$port/v1/trace")).map { uri =>
      object dsl extends Http4sClientDsl[F]
      import dsl._

      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] =
          for {
            req <- POST(Convert.toJsonString(batch), uri, `Content-Type`(MediaType.application.json))
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
