package io.janstenpickle.trace4cats.sttp.client3

import io.janstenpickle.trace4cats.model.AttributeValue.{LongValue, StringValue}
import io.janstenpickle.trace4cats.model.{AttributeValue, SemanticAttributeKeys}
import sttp.client3.Request

object SttpRequest {
  //credit : Regular Expressions Cookbook by Steven Levithan, Jan Goyvaerts
  private final val ipv4Regex =
    "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".r

  //credit : Regular Expressions Cookbook by Steven Levithan, Jan Goyvaerts
  private final val ipv6Regex =
    "^(?:(?:(?:[A-F0-9]{1,4}:){6}|(?=(?:[A-F0-9]{0,4}:){0,6}(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$)(([0-9A-F]{1,4}:){0,5}|:)((:[0-9A-F]{1,4}){1,5}:|:))(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}|(?=(?:[A-F0-9]{0,4}:){0,7}[A-F0-9]{0,4}$)(([0-9A-F]{1,4}:){1,7}|:)((:[0-9A-F]{1,4}){1,7}|:))$".r

  def toAttributes[T, R](req: Request[T, R]): Map[String, AttributeValue] =
    req.uri.host.map { host =>
      val key = host.toUpperCase match {
        case ipv4Regex(_*) => SemanticAttributeKeys.remoteServiceIpv4
        case ipv6Regex(_*) => SemanticAttributeKeys.remoteServiceIpv6
        case _ => SemanticAttributeKeys.remoteServiceHostname
      }

      key -> StringValue(host)
    }.toMap ++ req.uri.port.map(port => SemanticAttributeKeys.remoteServicePort -> LongValue(port.toLong))
}
