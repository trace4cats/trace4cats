package io.janstenpickle.trace4cats.opentelemetry.common

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

import io.opentelemetry.sdk.trace.export.{SpanExporter => OTSpanExporter}

import scala.jdk.CollectionConverters._

object OpenTelemetryGrpcSpanExporter {
  def apply[F[_]: Sync: ContextShift: Timer](
    blocker: Blocker,
    host: String,
    port: Int,
    makeExporter: (ManagedChannel, String) => OTSpanExporter
  ): Resource[F, SpanExporter[F]] =
    for {
      channel <- Resource.make[F, ManagedChannel](
        Sync[F].delay(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())
      )(channel => Sync[F].delay(channel.shutdown()).void)
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] =
          Resource
            .make(Sync[F].delay(makeExporter(channel, batch.process.serviceName)))(
              exporter => Sync[F].delay(exporter.flush()) >> Sync[F].delay(exporter.shutdown())
            )
            .use { exporter =>
              blocker.delay(
                exporter
                  .`export`(batch.spans.map(Trace4CatsSpanData(Trace4CatsResource(batch.process), _)).asJavaCollection)
              )

            }
            .void
      }
}
