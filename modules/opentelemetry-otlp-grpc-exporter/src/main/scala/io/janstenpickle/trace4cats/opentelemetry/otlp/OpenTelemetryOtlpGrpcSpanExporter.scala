package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Async, ContextShift, Resource, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter

object OpenTelemetryOtlpGrpcSpanExporter {
  def apply[F[_]: Async: ContextShift: Timer](
    host: String = "localhost",
    port: Int = 55680
  ): Resource[F, SpanExporter[F]] =
    OpenTelemetryGrpcSpanExporter(
      host,
      port,
      (channel, _) => OtlpGrpcSpanExporter.newBuilder().setChannel(channel).build()
    )
}
