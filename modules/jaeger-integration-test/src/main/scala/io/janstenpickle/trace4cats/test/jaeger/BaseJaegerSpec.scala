package io.janstenpickle.trace4cats.test.jaeger

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import fs2.Chunk
import io.circe.generic.auto._
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.http4s.circe.CirceEntityCodec._
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalacheck.Shrink
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait BaseJaegerSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {

  implicit val contextShift = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("JaegerSpanExport")

  def convertToJaegerAttributes(attributes: Map[String, AttributeValue]): List[JaegerTag] =
    attributes.toList.map {
      case (k, AttributeValue.StringValue(value)) => JaegerTag.StringTag(k, value.value)
      case (k, AttributeValue.BooleanValue(value)) => JaegerTag.BoolTag(k, value.value)
      case (k, AttributeValue.DoubleValue(value)) => JaegerTag.FloatTag(k, value.value)
      case (k, AttributeValue.LongValue(value)) => JaegerTag.LongTag(k, value.value)
      case (k, v: AttributeValue.AttributeList) => JaegerTag.StringTag(k, v.show)
    }

  def batchToJaegerResponse(
    batch: Batch[Chunk],
    process: TraceProcess,
    kindToAttributes: SpanKind => Map[String, AttributeValue],
    statusToAttributes: SpanStatus => Map[String, AttributeValue],
    processToAttributes: TraceProcess => Map[String, AttributeValue],
    additionalAttributes: Map[String, AttributeValue] = Map.empty,
    convertAttributes: Map[String, AttributeValue] => List[JaegerTag] = convertToJaegerAttributes,
    internalSpanFormat: String = "proto",
    followsFrom: Boolean = true
  ): List[JaegerTraceResponse] = {
    batch.spans.toList
      .groupBy(_.context.traceId)
      .toList
      .map { case (traceId, spans) =>
        JaegerTraceResponse(
          NonEmptyList
            .one(
              JaegerTrace(
                traceID = traceId.show,
                spans = spans
                  .map { span =>
                    val jtags = (JaegerTag.StringTag("internal.span.format", internalSpanFormat) :: convertAttributes(
                      span.allAttributes ++ kindToAttributes(span.kind) ++ statusToAttributes(
                        span.status
                      ) ++ additionalAttributes
                    )).sortBy(_.key)

                    val parentRefs = span.context.parent.toList.map { parent =>
                      JaegerReference("CHILD_OF", traceId.show, parent.spanId.show)
                    }

                    val linkRefs = span.links match {
                      case Some(links) if followsFrom =>
                        links.map { link =>
                          JaegerReference("FOLLOWS_FROM", link.traceId.show, link.spanId.show)
                        }.toList
                      case _ => List.empty[JaegerReference]
                    }

                    JaegerSpan(
                      traceID = traceId.show,
                      spanID = span.context.spanId.show,
                      operationName = span.name,
                      startTime = TimeUnit.MILLISECONDS.toMicros(span.start.toEpochMilli),
                      duration = TimeUnit.MILLISECONDS.toMicros(span.end.toEpochMilli) - TimeUnit.MILLISECONDS
                        .toMicros(span.start.toEpochMilli),
                      tags = jtags,
                      references = (parentRefs ++ linkRefs).sortBy(_.traceID)
                    )
                  }
                  .sortBy(_.operationName),
                processes = Map(
                  "p1" -> JaegerProcess(
                    process.serviceName,
                    convertAttributes(processToAttributes(process)).sortBy(_.key)
                  )
                )
              )
            )
        )
      }
      .sortBy(_.data.head.traceID)
  }

  def testExporter(
    exporter: Resource[IO, SpanExporter[IO, Chunk]],
    batch: Batch[Chunk],
    expectedResponse: List[JaegerTraceResponse]
  ): Assertion = {
    val res =
      BlazeClientBuilder[IO](ExecutionContext.global).resource
        .use { client =>
          exporter.use(_.exportBatch(batch)) >> timer
            .sleep(1.second) >> batch.spans
            .map(_.context.traceId)
            .toList
            .distinct
            .traverse { traceId =>
              client.expect[JaegerTraceResponse](s"http://localhost:16686/api/traces/${traceId.show}")
            }

        }
        .unsafeRunSync()
        .sortBy(_.data.head.traceID)
        .map(resp =>
          resp.copy(data =
            resp.data.map(trace =>
              trace.copy(spans = trace.spans.map(span => span.copy(references = span.references.sortBy(_.traceID))))
            )
          )
        )

    assert(res === expectedResponse)
  }

  def testCompleter(
    completer: Resource[IO, SpanCompleter[IO]],
    span: CompletedSpan.Builder,
    process: TraceProcess,
    expectedResponse: List[JaegerTraceResponse]
  ): Assertion = {
    val batch = Batch(List(span.build(process)))

    val res =
      BlazeClientBuilder[IO](ExecutionContext.global).resource
        .use { client =>
          completer.use(_.complete(span)) >> timer
            .sleep(1.second) >> batch.spans
            .map(_.context.traceId)
            .distinct
            .traverse { traceId =>
              client.expect[JaegerTraceResponse](s"http://localhost:16686/api/traces/${traceId.show}")
            }

        }
        .unsafeRunSync()
        .sortBy(_.data.head.traceID)
        .map(resp =>
          resp.copy(data =
            resp.data.map(trace =>
              trace.copy(spans = trace.spans.map(span => span.copy(references = span.references.sortBy(_.traceID))))
            )
          )
        )

    assert(res === expectedResponse)
  }
}
