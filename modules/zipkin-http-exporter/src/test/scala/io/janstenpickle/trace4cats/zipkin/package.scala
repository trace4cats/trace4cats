package io.janstenpickle.trace4cats

import cats.syntax.show._
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.JaegerTag

package object zipkin {

  // Jaeger automatically adds a 'span.kind' tag for server and client Zipkin spans, but does not otherwise
  def kindToAttributes(kind: SpanKind): Map[String, AttributeValue] = kind match {
    case SpanKind.Server => Map("span.kind" -> AttributeValue.StringValue("server"))
    case SpanKind.Client => Map("span.kind" -> AttributeValue.StringValue("client"))
    case _ => Map.empty
  }

  def processToAttributes: TraceProcess => Map[String, AttributeValue] = _ => Map.empty

  def convertAttributes(attributes: Map[String, AttributeValue]): List[JaegerTag] =
    attributes
      .filterNot { case (k, _) => k == "service.name" }
      .toList
      .map {
        case (k, AttributeValue.BooleanValue(value)) if k == "error" => JaegerTag.BoolTag(k, value.value)
        case (k, v) => JaegerTag.StringTag(k, v.show)
      }

  val excludedTagKeys: Set[String] = Set("lc")
}
