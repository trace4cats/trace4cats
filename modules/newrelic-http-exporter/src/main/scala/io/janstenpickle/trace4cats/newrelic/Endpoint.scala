package io.janstenpickle.trace4cats.newrelic

sealed trait Endpoint {
  def url: String
}
object Endpoint {
  private def standardUrl(subdomain: String) = s"https://$subdomain.newrelic.com/trace/v1"

  case object US extends Endpoint {
    override val url: String = standardUrl("trace-api")
  }
  case object EU extends Endpoint {
    override val url: String = standardUrl("trace-api.eu")
  }
  case class Observer(url: String) extends Endpoint
}
