package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.Foldable
import cats.effect.{Async, ContextShift, Resource, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter

object OpenTelemetryOtlpGrpcSpanExporter {
  def apply[F[_]: Async: ContextShift: Timer, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 55680
  ): Resource[F, SpanExporter[F, G]] =
    OpenTelemetryGrpcSpanExporter(host, port, channel => OtlpGrpcSpanExporter.newBuilder().setChannel(channel).build())
}
