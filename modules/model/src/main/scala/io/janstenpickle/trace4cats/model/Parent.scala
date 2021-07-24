package io.janstenpickle.trace4cats.model

import cats.Eq

case class Parent(spanId: SpanId, isRemote: Boolean)

object Parent {
  implicit val eq: Eq[Parent] = Eq.by(p => (p.spanId, p.isRemote))
}
