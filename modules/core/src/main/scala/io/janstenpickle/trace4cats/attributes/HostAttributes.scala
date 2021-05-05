package io.janstenpickle.trace4cats.attributes

import cats.effect.kernel.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.model.AttributeValue.StringValue
import io.janstenpickle.trace4cats.model.{AttributeValue, SemanticAttributeKeys}

import java.net.InetAddress

object HostAttributes {
  def apply[F[_]: Sync]: F[Map[String, AttributeValue]] =
    for {
      inetAddress <- Sync[F].delay(InetAddress.getLocalHost)
      ipv4 <- Sync[F].delay(Option(inetAddress.getHostAddress))
      hostname <- Sync[F].delay(Option(inetAddress.getCanonicalHostName))
    } yield ipv4.map(ip => SemanticAttributeKeys.serviceIpv4 -> StringValue(ip)).toMap ++ hostname.map(host =>
      SemanticAttributeKeys.serviceHostname -> StringValue(host)
    )
}
