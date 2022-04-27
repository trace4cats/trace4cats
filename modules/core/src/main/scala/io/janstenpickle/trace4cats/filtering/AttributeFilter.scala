package io.janstenpickle.trace4cats.filtering

import cats.Eq
import cats.data.{NonEmptyMap, NonEmptySet}
import cats.kernel.Semigroup
import io.janstenpickle.trace4cats.model.AttributeValue

object AttributeFilter {
  def names(attributeNames: NonEmptySet[String]): AttributeFilter = (key, _) => attributeNames.contains(key)

  def values(attributeValues: NonEmptySet[AttributeValue]): AttributeFilter =
    (_, value) => attributeValues.contains(value)

  def nameValues(nameValues: NonEmptyMap[String, AttributeValue]): AttributeFilter = { (key, value) =>
    nameValues.lookup(key).fold(false)(Eq.eqv(value, _))
  }

  def combined(x: AttributeFilter, y: AttributeFilter): AttributeFilter = { (key, value) =>
    x(key, value) || y(key, value)
  }

  implicit val semigroup: Semigroup[AttributeFilter] = new Semigroup[AttributeFilter] {
    override def combine(x: AttributeFilter, y: AttributeFilter): AttributeFilter = combined(x, y)
  }
}
