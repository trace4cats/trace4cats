package io.janstenpickle.trace4cats.http4s.client

import io.janstenpickle.trace4cats.http4s.common.Request_
import io.janstenpickle.trace4cats.model.AttributeValue.{LongValue, StringValue}
import io.janstenpickle.trace4cats.model.{AttributeValue, SemanticAttributeKeys}
import org.http4s.Uri

object Http4sClientRequest {
  def toAttributes(req: Request_): Map[String, AttributeValue] =
    req.uri.host.map {
      case Uri.Ipv4Address(address) => SemanticAttributeKeys.remoteServiceIpv4 -> StringValue(address.toString())
      case Uri.Ipv6Address(address) => SemanticAttributeKeys.remoteServiceIpv6 -> StringValue(address.toString())
      case Uri.RegName(host) => SemanticAttributeKeys.remoteServiceHostname -> StringValue(host.toString)
    }.toMap ++ req.uri.port.map(port => SemanticAttributeKeys.servicePort -> LongValue(port.toLong))
}
