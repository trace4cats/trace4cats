package io.janstenpickle.trace4cats.opentelemetry.common

import cats.Foldable
import cats.effect.{Async, ContextShift, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
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

  def apply[F[_]: Async: ContextShift: Timer, G[_]: Foldable](
    host: String,
    port: Int,
    makeExporter: ManagedChannel => OTSpanExporter
  ): Resource[F, SpanExporter[F, G]] = {
    def handleResult(onFailure: => Throwable)(code: CompletableResultCode): F[Unit] =
      Async[F].asyncF[Unit] { cb =>
        val complete = new Runnable {
          override def run(): Unit =
            if (code.isSuccess) cb(Right(()))
            else cb(Left(onFailure))
        }
        Sync[F].delay(code.whenComplete(complete)).void
      }

    for {
      channel <- Resource.make[F, ManagedChannel](
        Sync[F].delay(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())
      )(channel => Sync[F].delay(channel.shutdown()).void)
      exporter <- Resource.make(Sync[F].delay(makeExporter(channel)))(exporter =>
        Sync[F].delay(exporter.shutdown()).flatMap(handleResult(ShutdownFailure(host, port)))
      )
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] =
        handleResult(ExportFailure(host, port))(
          exporter
            .`export`(
              batch.spans
                .foldLeft(ListBuffer.empty[SpanData]) { (buf, span) =>
                  buf += Trace4CatsSpanData(Trace4CatsResource(span.serviceName), span)
                }
                .asJavaCollection
            )
        )
    }
  }
}
