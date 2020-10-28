package io.janstenpickle.trace4cats.jaeger

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.functor._
import cats.syntax.show._
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.jaegertracing.thriftjava.{Process, Span, Tag, TagType}
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.AttributeValue._
import io.janstenpickle.trace4cats.model.{AttributeValue, Batch, CompletedSpan, SampleDecision}

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
    val statusTags = SemanticTags.statusTags("span.")

    def makeTags(attributes: Map[String, AttributeValue]): java.util.List[Tag] =
      attributes.view
        .map {
          case (key, StringValue(value)) =>
            new Tag(key, TagType.STRING).setVStr(value)
          case (key, DoubleValue(value)) =>
            new Tag(key, TagType.DOUBLE).setVDouble(value)
          case (key, BooleanValue(value)) =>
            new Tag(key, TagType.BOOL).setVBool(value)
          case (key, LongValue(value)) =>
            new Tag(key, TagType.LONG).setVLong(value)
          case (key, value: AttributeList) =>
            new Tag(key, TagType.STRING).setVStr(value.show)
        }
        .toList
        .asJava

    def convert(span: CompletedSpan): Span = {
      val traceIdBuffer = ByteBuffer.wrap(span.context.traceId.value)
      val traceIdHigh = traceIdBuffer.getLong
      val traceIdLow = traceIdBuffer.getLong

      val startMicros = TimeUnit.MILLISECONDS.toMicros(span.start.toEpochMilli)
      val endMicros = TimeUnit.MILLISECONDS.toMicros(span.end.toEpochMilli)

      val thriftSpan = new Span(
        traceIdLow,
        traceIdHigh,
        ByteBuffer.wrap(span.context.spanId.value).getLong,
        span.context.parent.map(parent => ByteBuffer.wrap(parent.spanId.value).getLong).getOrElse(0),
        span.name,
        span.context.traceFlags.sampled match {
          case SampleDecision.Include => 0
          case SampleDecision.Drop => 1
        },
        startMicros,
        endMicros - startMicros
      )

      thriftSpan.setTags(makeTags(span.attributes ++ statusTags(span.status) ++ SemanticTags.kindTags(span.kind)))
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
