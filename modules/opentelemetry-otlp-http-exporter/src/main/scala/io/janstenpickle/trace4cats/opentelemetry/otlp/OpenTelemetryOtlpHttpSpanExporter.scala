package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Sync, Timer}
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

object OpenTelemetryOtlpHttpSpanExporter {
  def blazeClient[F[_]: ConcurrentEffect: Timer: ContextShift](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 55681
  ): Resource[F, SpanExporter[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource.evalMap(apply[F](_, host, port))

  def apply[F[_]: Sync: Timer](client: Client[F], host: String = "localhost", port: Int = 55681): F[SpanExporter[F]] =
    HttpSpanExporter[F, String](client, s"http://$host:$port/v1/trace", (batch: Batch) => Convert.toJsonString(batch))
}
