package io.janstenpickle.trace4cats.attributes

import cats.effect.kernel.Sync
import cats.syntax.functor._
import io.janstenpickle.trace4cats.model.AttributeValue
import io.janstenpickle.trace4cats.model.AttributeValue.StringValue

import scala.jdk.CollectionConverters._

object SystemPropertyAttributes {
  def apply[F[_]: Sync](): F[Map[String, AttributeValue]] = filterKeys(_ => true)

  def excludeKeys[F[_]: Sync](excludeKeys: Set[String]): F[Map[String, AttributeValue]] = filterKeys(
    !excludeKeys.contains(_)
  )

  def filterKeys[F[_]: Sync](filter: String => Boolean): F[Map[String, AttributeValue]] =
    Sync[F].delay(System.getProperties).map { properties =>
      val keys = properties.stringPropertyNames().asScala.filter(filter)

      keys.map(k => k -> StringValue(properties.getProperty(k))).toMap
    }

  def includeKeys[F[_]: Sync](includeKeys: Set[String]): F[Map[String, AttributeValue]] =
    Sync[F].delay(System.getProperties).map { properties =>
      includeKeys.flatMap(k => Option(properties.getProperty(k)).map(v => k -> StringValue(v))).toMap
    }
}
