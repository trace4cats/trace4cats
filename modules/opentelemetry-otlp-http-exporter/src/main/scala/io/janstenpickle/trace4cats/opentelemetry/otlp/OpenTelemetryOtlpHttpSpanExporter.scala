package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

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
    HttpSpanExporter[F, String](client, s"http://$host:$port/v1/trace", (batch: Batch) => Convert.toJsonString(batch))
}
