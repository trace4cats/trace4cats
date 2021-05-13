package io.janstenpickle.trace4cats.http4s.client

import io.janstenpickle.trace4cats.http4s.common.Request_
import io.janstenpickle.trace4cats.model.AttributeValue.{LongValue, StringValue}
import io.janstenpickle.trace4cats.model.{AttributeValue, SemanticAttributeKeys}
import org.http4s.Uri

object Http4sClientRequest {
  def toAttributes(req: Request_): Map[String, AttributeValue] =
    req.uri.host.map { host =>
      val key = host match {
        case _: Uri.Ipv4Address => SemanticAttributeKeys.remoteServiceIpv4
        case _: Uri.Ipv6Address => SemanticAttributeKeys.remoteServiceIpv6
        case _: Uri.RegName => SemanticAttributeKeys.remoteServiceHostname
      }
      key -> StringValue(host.value)
    }.toMap ++ req.uri.port.map(port => SemanticAttributeKeys.servicePort -> LongValue(port.toLong))
}
