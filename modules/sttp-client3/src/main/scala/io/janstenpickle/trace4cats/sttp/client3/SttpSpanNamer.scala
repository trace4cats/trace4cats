package io.janstenpickle.trace4cats.sttp.client3

object SttpSpanNamer {
  val method: SttpSpanNamer = _.method.method

  val path: SttpSpanNamer = req => req.uri.path.mkString("/")

  val methodWithPath: SttpSpanNamer = req => s"${req.method.method} ${req.uri.path.mkString("/")}"

  /** Similar to `methodWithPath`, but allows one to reduce the cardinality of the operation name by applying
    * a transformation to each path segment, e.g.:
    * {{{
    *   methodWithPartiallyTransformedPath {
    *     case s if s.toLongOption.isDefined => "{long}"
    *     case s if scala.util.Try(java.util.UUID.fromString(s)).isSuccess => "{uuid}"
    *   }
    * }}}
    *
    * Note that regex matching should generally be preferred over try-catching conversion failures.
    */
  def methodWithPartiallyTransformedPath(transform: PartialFunction[String, String]): SttpSpanNamer =
    req => {
      val method = req.method.method
      val path = req.uri.path
        .map(s => transform.applyOrElse(s, identity[String]))
        .mkString("/")
      if (path.isEmpty) method else s"$method $path"
    }
}
