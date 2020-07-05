package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter

object OpenTelemetryOtlpGrpcSpanExporter {
  def apply[F[_]: Sync: ContextShift: Timer](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 55680
  ): Resource[F, SpanExporter[F]] =
    OpenTelemetryGrpcSpanExporter(
      blocker,
      host,
      port,
      (channel, _) => OtlpGrpcSpanExporter.newBuilder().setChannel(channel).build()
    )
}
