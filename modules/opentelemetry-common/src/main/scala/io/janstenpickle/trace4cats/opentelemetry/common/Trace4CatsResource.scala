package io.janstenpickle.trace4cats.opentelemetry.common

import io.opentelemetry.common.Attributes
import io.opentelemetry.sdk.resources.Resource

object Trace4CatsResource {
  def apply(serviceName: String): Resource =
    Resource.create(Attributes.newBuilder().setAttribute("service.name", serviceName).build())
}
