package io.janstenpickle.trace4cats.opentelemetry.jaeger

import cats.Foldable
import cats.effect.{Async, ContextShift, Resource, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter

object OpenTelemetryJaegerSpanExporter {
  def apply[F[_]: Async: ContextShift: Timer, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 14250,
    serviceName: String = "unknown"
  ): Resource[F, SpanExporter[F, G]] =
    OpenTelemetryGrpcSpanExporter(
      host,
      port,
      channel => JaegerGrpcSpanExporter.builder().setChannel(channel).setServiceName(serviceName).build()
    )

}
