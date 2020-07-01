package io.janstenpickle.trace4cats.model

import cats.Eq
import cats.instances.boolean._

case class Parent(spanId: SpanId, isRemote: Boolean)

object Parent {
  implicit val eq: Eq[Parent] = cats.derived.semi.eq[Parent]
}
