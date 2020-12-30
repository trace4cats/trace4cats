package io.janstenpickle.trace4cats.sttp.tapir

import sttp.tapir.internal._

object TapirSpanNamer {
  def method[I]: TapirSpanNamer[I] = (ep, _) => ep.input.method.fold("ANY")(_.method)
  def pathTemplate[I]: TapirSpanNamer[I] = (ep, _) => ep.renderPathTemplate()
  def methodWithPathTemplate[I]: TapirSpanNamer[I] = (ep, _) =>
    ep.input.method.fold("ANY")(_.method) + " " + ep.renderPathTemplate()
}
