package io.janstenpickle.trace4cats.test.jaeger

import cats.data.NonEmptyList
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.auto._
import io.circe.generic.semiauto._

case class JaegerTraceResponse(data: NonEmptyList[JaegerTrace])

case class JaegerTrace(traceID: String, spans: List[JaegerSpan], processes: Map[String, JaegerProcess])

object JaegerTrace {
  implicit val decoder: Decoder[JaegerTrace] = deriveDecoder[JaegerTrace].map { trace =>
    trace.copy(spans = trace.spans.sortBy(_.operationName))
  }
}

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

  implicit val decoder: Decoder[JaegerTag] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "string" => deriveDecoder[StringTag].tryDecode(cursor)
      case "bool" => deriveDecoder[BoolTag].tryDecode(cursor)
      case "float64" => deriveDecoder[FloatTag].tryDecode(cursor)
      case "int64" => deriveDecoder[LongTag].tryDecode(cursor)
      case t => Left(DecodingFailure(s"Invalid tag type $t", List.empty))
    }
  }
}

case class JaegerReference(refType: String, traceID: String, spanID: String)
