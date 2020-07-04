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

  def statusTags(prefix: String, requireMessage: Boolean = true): SpanStatus => Map[String, TraceValue] = { s =>
    val attrs = Map[String, TraceValue](s"${prefix}status.code" -> s.canonicalCode)
    val errorAttrs: Map[String, TraceValue] = if (s.isOk) attrs else attrs + ("error" -> true)

    if (requireMessage) errorAttrs + (s"${prefix}status.message" -> "") else errorAttrs
  }
}
