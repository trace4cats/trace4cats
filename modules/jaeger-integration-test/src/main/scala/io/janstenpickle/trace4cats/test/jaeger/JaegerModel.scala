package io.janstenpickle.trace4cats.test.jaeger

import cats.data.NonEmptyList
import cats.syntax.functor._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.generic.semiauto._

case class JaegerTraceResponse(data: NonEmptyList[JaegerTrace])

case class JaegerTrace(traceID: String, spans: List[JaegerSpan], processes: Map[String, JaegerProcess])

case class JaegerSpan(
  traceID: String,
  spanID: String,
  operationName: String,
  startTime: Long,
  duration: Long,
  tags: List[JaegerTag],
  references: List[JaegerReference]
)

object JaegerSpan {
  implicit val decoder: Decoder[JaegerSpan] = deriveDecoder[JaegerSpan].map { span =>
    span.copy(tags = span.tags.sortBy(_.key))
  }
}

case class JaegerProcess(serviceName: String, tags: List[JaegerTag])

object JaegerProcess {
  implicit val decoder: Decoder[JaegerProcess] = deriveDecoder[JaegerProcess].map { proc =>
    proc.copy(
      tags = proc.tags
        .filterNot(tag => tag.key == "hostname" || tag.key == "ip" || tag.key == "jaeger.version")
        .sortBy(_.key)
    )
  }
}

sealed trait JaegerTag {
  def key: String
}
object JaegerTag {
  case class StringTag(key: String, value: String, `type`: String = "string") extends JaegerTag
  case class BoolTag(key: String, value: Boolean, `type`: String = "bool") extends JaegerTag
  case class LongTag(key: String, value: Long, `type`: String = "int64") extends JaegerTag
  case class FloatTag(key: String, value: Double, `type`: String = "float64") extends JaegerTag

  implicit val decoder: Decoder[JaegerTag] =
    deriveDecoder[StringTag]
      .widen[JaegerTag]
      .or(deriveDecoder[BoolTag].widen)
      .or(deriveDecoder[LongTag].widen)
      .or(deriveDecoder[FloatTag].widen)
}

case class JaegerReference(refType: String, traceID: String, spanID: String)
