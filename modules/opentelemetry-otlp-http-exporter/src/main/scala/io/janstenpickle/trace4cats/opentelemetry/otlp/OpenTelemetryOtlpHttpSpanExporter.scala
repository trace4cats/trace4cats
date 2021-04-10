package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.Foldable
import cats.effect.{ConcurrentEffect, Resource, Sync}
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext
import cats.effect.Temporal

object OpenTelemetryOtlpHttpSpanExporter {
  def blazeClient[F[_]: ConcurrentEffect: Temporal, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 55681,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] =
    // TODO: CE3 - use Async[F].executionContext
    BlazeClientBuilder[F](ec.getOrElse(ExecutionContext.global)).resource.evalMap(apply[F, G](_, host, port))

  def apply[F[_]: Sync: Temporal, G[_]: Foldable](
    client: Client[F],
    host: String = "localhost",
    port: Int = 55681
  ): F[SpanExporter[F, G]] =
    HttpSpanExporter[F, G, String](
      client,
      s"http://$host:$port/v1/trace",
      (batch: Batch[G]) => Convert.toJsonString(batch)
    )
}
