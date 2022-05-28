package trace4cats.attributes

import cats.effect.kernel.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import trace4cats.model.AttributeValue.StringValue
import trace4cats.model.{AttributeValue, SemanticAttributeKeys}

import java.net.InetAddress

object HostAttributes {
  def apply[F[_]: Sync]: F[Map[String, AttributeValue]] =
    for {
      inetAddress <- Sync[F].blocking(InetAddress.getLocalHost)
      ipv4 <- Sync[F].blocking(Option(inetAddress.getHostAddress))
      hostname <- Sync[F].blocking(Option(inetAddress.getCanonicalHostName))
    } yield ipv4.map(ip => SemanticAttributeKeys.serviceIpv4 -> StringValue(ip)).toMap ++ hostname.map(host =>
      SemanticAttributeKeys.serviceHostname -> StringValue(host)
    )
}
