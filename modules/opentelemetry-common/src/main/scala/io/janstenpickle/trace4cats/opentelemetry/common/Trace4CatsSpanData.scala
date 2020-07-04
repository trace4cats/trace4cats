package io.janstenpickle.trace4cats.opentelemetry.common

import java.nio.ByteBuffer
import java.util
import java.util.concurrent.TimeUnit

import io.janstenpickle.trace4cats.model.TraceState.{Key, Value}
import io.janstenpickle.trace4cats.model.{CompletedSpan, SpanKind, SpanStatus}
import io.opentelemetry.common.{AttributeValue, ReadableAttributes}
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace._

import scala.jdk.CollectionConverters._

object Trace4CatsSpanData {

  def apply(resource: Resource, span: CompletedSpan): SpanData = new SpanData {
    override lazy val getTraceId: TraceId = {
      val traceIdBuffer = ByteBuffer.wrap(span.context.traceId.value)
      val traceIdHigh = traceIdBuffer.getLong
      val traceIdLow = traceIdBuffer.getLong
      new TraceId(traceIdHigh, traceIdLow)
    }

    override lazy val getSpanId: SpanId = {
      val spanIdBuffer = ByteBuffer.wrap(span.context.spanId.value)
      new SpanId(spanIdBuffer.getLong)
    }

    override lazy val getTraceFlags: TraceFlags =
      TraceFlags.builder().setIsSampled(span.context.traceFlags.sampled).build()

    override lazy val getTraceState: TraceState =
      span.context.traceState.values
        .foldLeft(TraceState.builder()) {
          case (builder, (Key(k), Value(v))) => builder.set(k, v)
        }
        .build()

    override lazy val getParentSpanId: SpanId =
      span.context.parent
        .map { parent =>
          val spanIdBuffer = ByteBuffer.wrap(parent.spanId.value)
          new SpanId(spanIdBuffer.getLong)
        }
        .getOrElse(SpanId.getInvalid)

    override def getResource: Resource = resource

    override lazy val getInstrumentationLibraryInfo: InstrumentationLibraryInfo =
      InstrumentationLibraryInfo.create("trace4cats", null)

    override def getName: String = span.name

    override lazy val getKind: Span.Kind =
      span.kind match {
        case SpanKind.Client => Span.Kind.CLIENT
        case SpanKind.Server => Span.Kind.SERVER
        case SpanKind.Internal => Span.Kind.INTERNAL
        case SpanKind.Producer => Span.Kind.PRODUCER
        case SpanKind.Consumer => Span.Kind.CONSUMER
      }

    override lazy val getStartEpochNanos: Long = TimeUnit.MICROSECONDS.toNanos(span.start)

    override val getAttributes: ReadableAttributes = Trace4CatsReadableAttributes(
      toAttributes[Map[String, AttributeValue]](
        span.attributes,
        Map.empty[String, AttributeValue],
        (map, k, v) => map.updated(k, v)
      )
    )

    override lazy val getEvents: util.List[SpanData.Event] = List.empty.asJava

    override lazy val getLinks: util.List[SpanData.Link] = List.empty.asJava

    override lazy val getStatus: Status =
      span.status match {
        case SpanStatus.Ok => Status.OK
        case SpanStatus.Cancelled => Status.CANCELLED
        case SpanStatus.Internal => Status.INTERNAL
      }

    override lazy val getEndEpochNanos: Long = TimeUnit.MICROSECONDS.toNanos(span.end)

    override lazy val getHasRemoteParent: Boolean = span.context.parent.fold(false)(_.isRemote)

    override lazy val getHasEnded: Boolean = true

    override def getTotalRecordedEvents: Int = 0

    override def getTotalRecordedLinks: Int = 0

    override lazy val getTotalAttributeCount: Int =
      span.attributes.size
  }

}
