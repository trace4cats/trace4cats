package io.janstenpickle.trace4cats.opentelemetry.jaeger

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter

object OpenTelemetryJaegerSpanExporter {
  def apply[F[_]: Sync: ContextShift: Timer](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 14250
  ): Resource[F, SpanExporter[F]] =
    OpenTelemetryGrpcSpanExporter(
      blocker,
      host,
      port,
      (channel, serviceName) =>
        JaegerGrpcSpanExporter.newBuilder().setChannel(channel).setServiceName(serviceName).build()
    )

}
