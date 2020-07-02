package io.janstenpickle.trace4cats.jaeger

import java.nio.ByteBuffer

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.functor._
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.jaegertracing.thriftjava.{Process, Span, Tag, TagType}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.TraceValue._
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceValue}

import scala.jdk.CollectionConverters._
import scala.util.Try

object JaegerSpanExporter {
  def apply[F[_]: Concurrent: ContextShift: Timer](
    blocker: Blocker,
    host: String = Option(System.getenv("JAEGER_AGENT_HOST")).getOrElse(UdpSender.DEFAULT_AGENT_UDP_HOST),
    port: Int = Option(System.getenv("JAEGER_AGENT_PORT"))
      .flatMap(p => Try(p.toInt).toOption)
      .getOrElse(UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT)
  ): Resource[F, SpanExporter[F]] = {
    def makeTags(attributes: Map[String, TraceValue]): java.util.List[Tag] =
      attributes.view
        .map {
          case (key, StringValue(value)) =>
            val tag = new Tag(key, TagType.STRING)
            tag.setVStr(value)
          case (key, DoubleValue(value)) =>
            val tag = new Tag(key, TagType.DOUBLE)
            tag.setVDouble(value)
          case (key, BooleanValue(value)) =>
            val tag = new Tag(key, TagType.BOOL)
            tag.setVBool(value)
        }
        .toList
        .asJava

    def convert(span: CompletedSpan): Span = {
      val traceIdBuffer = ByteBuffer.wrap(span.context.traceId.value)
      val traceIdLow = traceIdBuffer.getLong
      val traceIdHigh = traceIdBuffer.getLong

      val thriftSpan = new Span(
        traceIdLow,
        traceIdHigh,
        ByteBuffer.wrap(span.context.spanId.value).getLong,
        span.context.parent.map(parent => ByteBuffer.wrap(parent.spanId.value).getLong).getOrElse(0),
        span.name,
        if (span.context.traceFlags.sampled) 1 else 0,
        span.start,
        span.end - span.start
      )

      thriftSpan.setTags(makeTags(span.attributes))
    }

    Resource.make(Sync[F].delay(new UdpSender(host, port, 0)))(sender => Sync[F].delay(sender.close()).void).map {
      sender =>
        new SpanExporter[F] {
          override def exportBatch(batch: Batch): F[Unit] = {
            val process = new Process(batch.process.serviceName).setTags(makeTags(batch.process.attributes))
            val spans = batch.spans.map(convert).asJava

            blocker.delay(sender.send(process, spans))
          }
        }
    }
  }
}
