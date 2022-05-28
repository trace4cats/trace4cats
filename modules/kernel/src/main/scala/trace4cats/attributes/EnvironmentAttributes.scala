package trace4cats.attributes

import cats.effect.kernel.Sync
import cats.syntax.functor._
import trace4cats.model.AttributeValue
import trace4cats.model.AttributeValue.StringValue

import scala.jdk.CollectionConverters._

object EnvironmentAttributes {
  def apply[F[_]: Sync]: F[Map[String, AttributeValue]] = filterKeys(_ => true)

  def excludeKeys[F[_]: Sync](excludeKeys: Set[String]): F[Map[String, AttributeValue]] = filterKeys(
    !excludeKeys.contains(_)
  )

  def filterKeys[F[_]: Sync](filterKeys: String => Boolean): F[Map[String, AttributeValue]] =
    Sync[F]
      .delay(System.getenv())
      .map(_.asScala.toMap.flatMap { case (k, v) =>
        if (filterKeys(k)) Some(k -> StringValue(v)) else None
      })

  def includeKeys[F[_]: Sync](includeKeys: Set[String]): F[Map[String, AttributeValue]] =
    Sync[F].delay(System.getenv()).map { env =>
      includeKeys.flatMap(k => Option(env.get(k)).map(v => k -> StringValue(v))).toMap
    }
}
