package trace4cats.model

object SemanticAttributeKeys {
  // local service attributes
  private final val service = "service."
  final val servicePort: String = service + "port"
  final val serviceIpv4: String = service + "ipv4"
  final val serviceIpv6: String = service + "ipv6"
  final val serviceHostname: String = service + "hostname"

  // remote service attributes
  private final val remote = "remote."
  final val remoteServicePort: String = remote + servicePort
  final val remoteServiceIpv4: String = remote + serviceIpv4
  final val remoteServiceIpv6: String = remote + serviceIpv6
  final val remoteServiceHostname: String = remote + serviceHostname

  // http attributes
  private final val http = "http."
  final val httpFlavor: String = http + "flavor"
  final val httpMethod: String = http + "method"
  final val httpUrl: String = http + "url"
  final val httpStatusCode: String = http + "status_code"
  final val httpStatusMessage: String = http + "status_message"

}
