package io.janstenpickle.trace4cats.opentelemetry.common

import cats.Foldable
import cats.effect.kernel.{Resource, Sync}
import cats.syntax.foldable._
import cats.syntax.functor._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.opentelemetry.common.LiftCompletableResultCode._
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.{SpanExporter => OTSpanExporter}

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

object OpenTelemetryGrpcSpanExporter {
  case class ShutdownFailure(host: String, port: Int) extends RuntimeException {
    override def getMessage: String = s"Failed to shutdown Open Telemetry span exporter for $host:$port"
  }
  case class ExportFailure(host: String, port: Int) extends RuntimeException {
    override def getMessage: String = s"Failed to export Open Telemetry span batch to $host:$port"
  }

  def apply[F[_]: Sync: LiftCompletableResultCode, G[_]: Foldable](
    host: String,
    port: Int,
    makeExporter: ManagedChannel => OTSpanExporter
  ): Resource[F, SpanExporter[F, G]] = {
    for {
      channel <- Resource.make[F, ManagedChannel](
        Sync[F].delay(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())
      )(channel => Sync[F].delay(channel.shutdown()).void)
      exporter <- Resource.make(Sync[F].delay(makeExporter(channel)))(exporter =>
        Sync[F].delay(exporter.shutdown()).liftResultCode(ShutdownFailure(host, port))
      )
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] =
        Sync[F]
          .delay(
            exporter
              .`export`(
                batch.spans
                  .foldLeft(ListBuffer.empty[SpanData]) { (buf, span) =>
                    buf += Trace4CatsSpanData(Trace4CatsResource(span.serviceName), span)
                  }
                  .asJavaCollection
              )
          )
          .liftResultCode(ExportFailure(host, port))
    }
  }
}
