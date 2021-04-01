package io.janstenpickle.trace4cats.opentelemetry.common

import cats.Foldable
import cats.effect.{Async, ContextShift, Resource, Sync}
import cats.effect.syntax.bracket._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.export.{SpanExporter => OTSpanExporter}

import scala.jdk.CollectionConverters._
import cats.syntax.foldable._
import io.opentelemetry.sdk.trace.data.SpanData

import scala.collection.mutable.ListBuffer

object OpenTelemetryGrpcSpanExporter {
  case class ShutdownFailure(host: String, port: Int) extends RuntimeException {
    override def getMessage: String = s"Failed to shutdown Open Telemetry span exporter for $host:$port"
  }
  case class ExportFailure(host: String, port: Int) extends RuntimeException {
    override def getMessage: String = s"Failed to export Open Telemetry span batch to $host:$port"
  }

  def apply[F[_]: Async: ContextShift, G[_]: Foldable](
    host: String,
    port: Int,
    makeExporter: ManagedChannel => OTSpanExporter
  ): Resource[F, SpanExporter[F, G]] = {
    def liftCompletableResultCode(fa: F[CompletableResultCode])(onFailure: => Throwable): F[Unit] =
      fa.flatMap { result =>
        Async[F].async[Unit] { cb =>
          val _ = result.whenComplete { () =>
            if (result.isSuccess) cb(Right(()))
            else cb(Left(onFailure))
          }
        }
      }.guarantee(ContextShift[F].shift)

    def write(exporter: OTSpanExporter, spans: G[CompletedSpan]): F[Unit] = {
      for {
        spans <- Sync[F].delay(
          spans
            .foldLeft(ListBuffer.empty[SpanData]) { (buf, span) =>
              buf += Trace4CatsSpanData(Trace4CatsResource(span.serviceName), span)
            }
            .asJavaCollection
        )
        _ <- liftCompletableResultCode(Sync[F].delay(exporter.`export`(spans)))(ExportFailure(host, port))
      } yield ()
    }

    for {
      channel <- Resource.make[F, ManagedChannel](
        Sync[F].delay(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())
      )(channel => Sync[F].delay(channel.shutdown()).void)
      exporter <- Resource.make(Sync[F].delay(makeExporter(channel)))(exporter =>
        liftCompletableResultCode(Sync[F].delay(exporter.shutdown()))(ShutdownFailure(host, port))
      )
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = write(exporter, batch.spans)
    }
  }
}
