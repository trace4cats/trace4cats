package io.janstenpickle.trace4cats.`export`

import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceValue}

object SemanticTags {
  val kindTags: SpanKind => Map[String, TraceValue] = {
    case SpanKind.Internal => Map[String, TraceValue]("span.kind" -> "internal")
    case SpanKind.Client => Map[String, TraceValue]("span.kind" -> "client")
    case SpanKind.Server => Map[String, TraceValue]("span.kind" -> "server")
    case SpanKind.Producer => Map[String, TraceValue]("span.kind" -> "producer")
    case SpanKind.Consumer => Map[String, TraceValue]("span.kind" -> "consumer")
  }

  def statusTags(prefix: String, requireMessage: Boolean = true): SpanStatus => Map[String, TraceValue] = {
    case SpanStatus.Ok =>
      val attrs = Map[String, TraceValue](s"${prefix}status.code" -> 0)
      if (requireMessage) attrs + (s"${prefix}status.message" -> "") else attrs

    case SpanStatus.Cancelled =>
      val attrs = Map[String, TraceValue]("error" -> true, s"${prefix}status.code" -> 1)
      if (requireMessage) attrs + (s"${prefix}status.message" -> "") else attrs

    case SpanStatus.Internal =>
      val attrs = Map[String, TraceValue]("error" -> true, s"${prefix}status.code" -> 13)
      if (requireMessage) attrs + (s"${prefix}status.message" -> "") else attrs

  }
}
