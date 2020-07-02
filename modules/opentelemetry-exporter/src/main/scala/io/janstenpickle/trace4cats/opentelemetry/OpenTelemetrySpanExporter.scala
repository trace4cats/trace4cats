package io.janstenpickle.trace4cats.opentelemetry

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter

import scala.jdk.CollectionConverters._

object OpenTelemetrySpanExporter {
  def apply[F[_]: Sync: ContextShift: Timer](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 55678
  ): Resource[F, SpanExporter[F]] =
    for {
      channel <- Resource.make[F, ManagedChannel](
        Sync[F].delay(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())
      )(channel => Sync[F].delay(channel.shutdown()).void)
      exporter <- Resource.make(Sync[F].delay(OtlpGrpcSpanExporter.newBuilder().setChannel(channel).build()))(
        exporter => Sync[F].delay(exporter.flush()) >> Sync[F].delay(exporter.shutdown())
      )
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] =
          blocker
            .delay(
              exporter
                .`export`(batch.spans.map(Trace4CatsSpanData(Trace4CatsResource(batch.process), _)).asJavaCollection)
            )
            .void
      }
}
