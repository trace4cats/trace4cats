package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.Foldable
import cats.effect.kernel.{Async, Resource}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter

object OpenTelemetryOtlpGrpcSpanExporter {
  def apply[F[_]: Async, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 55680
  ): Resource[F, SpanExporter[F, G]] =
    OpenTelemetryGrpcSpanExporter(host, port, channel => OtlpGrpcSpanExporter.builder().setChannel(channel).build())
}
