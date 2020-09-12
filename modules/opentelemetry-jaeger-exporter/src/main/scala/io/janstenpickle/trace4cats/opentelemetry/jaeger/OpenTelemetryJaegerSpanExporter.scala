package io.janstenpickle.trace4cats.opentelemetry.jaeger

import cats.effect.{Async, ContextShift, Resource, Timer}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.common.OpenTelemetryGrpcSpanExporter
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter

object OpenTelemetryJaegerSpanExporter {
  def apply[F[_]: Async: ContextShift: Timer](
    host: String = "localhost",
    port: Int = 14250
  ): Resource[F, SpanExporter[F]] =
    OpenTelemetryGrpcSpanExporter(
      host,
      port,
      (channel, serviceName) =>
        JaegerGrpcSpanExporter.newBuilder().setChannel(channel).setServiceName(serviceName).build()
    )

}
