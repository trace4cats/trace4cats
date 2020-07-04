package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter

object OpenTelemetryOtlpSpanExporter {
  def apply[F[_]: Sync: ContextShift: Timer](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 55678
  ): Resource[F, SpanExporter[F]] =
    OpenTelemetryGrpcSpanExporter(blocker, host, port, OtlpGrpcSpanExporter.newBuilder().setChannel(_).build())
}
