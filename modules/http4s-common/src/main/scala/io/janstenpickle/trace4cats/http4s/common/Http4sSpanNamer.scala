package io.janstenpickle.trace4cats.http4s.common

import org.http4s.Uri

object Http4sSpanNamer {
  val method: Http4sSpanNamer = _.method.name

  val path: Http4sSpanNamer = req => Uri.decode(req.pathInfo.toString)

  val methodWithPath: Http4sSpanNamer = req => s"${req.method.name} ${Uri.decode(req.pathInfo.toString)}"

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
  def methodWithPartiallyTransformedPath(transform: PartialFunction[String, String]): Http4sSpanNamer =
    req => {
      val method = req.method.name
      val path = req.pathInfo.segments
        .map(s => transform.applyOrElse(s.decoded(), identity[String]))
        .mkString("/")
      if (path.isEmpty) method else s"$method $path"
    }
}
