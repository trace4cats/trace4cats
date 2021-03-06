package io.janstenpickle.trace4cats.opentelemetry.jaeger

import cats.Foldable
import cats.effect.kernel.{Async, Resource}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter

import scala.concurrent.ExecutionContext

object OpenTelemetryJaegerSpanExporter {
  def apply[F[_]: Async, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 14250,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] =
    OpenTelemetryGrpcSpanExporter(
      host,
      port,
      channel => JaegerGrpcSpanExporter.builder().setChannel(channel).build(),
      ec
    )
}
