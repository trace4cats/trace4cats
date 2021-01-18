package io.janstenpickle.trace4cats.opentelemetry.common

import java.util
import java.util.concurrent.TimeUnit
import cats.syntax.show._
import io.janstenpickle.trace4cats.model.SpanStatus._
import io.janstenpickle.trace4cats.model.TraceState.{Key, Value}
import io.janstenpickle.trace4cats.model.{CompletedSpan, SpanKind}
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.{EventData, LinkData, SpanData, StatusData}
import io.opentelemetry.api.trace._

import scala.jdk.CollectionConverters._

object Trace4CatsSpanData {

  def apply(resource: Resource, span: CompletedSpan): SpanData =
    new SpanData {
      override lazy val getTraceId: String = span.context.traceId.show

      override lazy val getSpanId: String = span.context.spanId.show

      override lazy val isSampled: Boolean = span.context.traceFlags.sampled.toBoolean

      override lazy val getTraceState: TraceState =
        span.context.traceState.values
          .foldLeft(TraceState.builder()) { case (builder, (Key(k), Value(v))) =>
            builder.set(k, v)
          }
          .build()

      override lazy val getParentSpanId: String =
        span.context.parent.fold(SpanId.getInvalid)(_.spanId.show)

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

      override lazy val getStartEpochNanos: Long = TimeUnit.MILLISECONDS.toNanos(span.start.toEpochMilli)

      override val getAttributes: Attributes = Trace4CatsAttributes(span.allAttributes)

      override lazy val getEvents: util.List[EventData] = List.empty.asJava

      override lazy val getLinks: util.List[LinkData] =
        span.links
          .fold(List.empty[LinkData])(_.map { link =>
            LinkData.create(
              SpanContext.create(link.traceId.show, link.spanId.show, TraceFlags.getSampled, TraceState.getDefault)
            )
          }.toList)
          .asJava

      override lazy val getStatus: StatusData =
        span.status match {
          case Ok => StatusData.ok()
          case Internal(message) => StatusData.create(StatusCode.ERROR, message)
          case _ => StatusData.error()
        }

      override lazy val getEndEpochNanos: Long = TimeUnit.MILLISECONDS.toNanos(span.end.toEpochMilli)

      override lazy val getParentSpanContext: SpanContext =
        span.context.parent.fold(SpanContext.getInvalid) { p =>
          SpanContext.create(span.context.traceId.show, p.spanId.show, TraceFlags.getSampled, TraceState.getDefault)
        }

      override def hasEnded: Boolean = true

      override def getTotalRecordedEvents: Int = 0

      override def getTotalRecordedLinks: Int = 0

      override lazy val getTotalAttributeCount: Int =
        span.allAttributes.size
    }

}
