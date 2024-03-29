package trace4cats

object SemanticTags {
  val kindString: SpanKind => String = {
    case SpanKind.Internal => "internal"
    case SpanKind.Client => "client"
    case SpanKind.Server => "server"
    case SpanKind.Producer => "producer"
    case SpanKind.Consumer => "consumer"
  }

  val kindTags: SpanKind => Map[String, AttributeValue] =
    kindString.andThen { kind =>
      Map[String, AttributeValue]("span.kind" -> kind)
    }

  def statusTags(
    prefix: String,
    statusCode: SpanStatus => Int = _.canonicalCode,
    requireMessage: Boolean = true
  ): SpanStatus => Map[String, AttributeValue] = { s =>
    val attrs = Map[String, AttributeValue](s"${prefix}status.code" -> statusCode(s))
    val errorAttrs: Map[String, AttributeValue] =
      s match {
        case SpanStatus.Internal(message) =>
          attrs ++ Map[String, AttributeValue]("error" -> true, s"${prefix}status.message" -> message)
        case s if s.isOk => attrs
        case _ => attrs + ("error" -> true)
      }
    if (requireMessage) Map[String, AttributeValue](s"${prefix}status.message" -> "") ++ errorAttrs else errorAttrs
  }

  val processTags: TraceProcess => Map[String, AttributeValue] = _.attributes
}
