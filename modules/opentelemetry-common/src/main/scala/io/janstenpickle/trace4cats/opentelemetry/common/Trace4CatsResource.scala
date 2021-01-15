package io.janstenpickle.trace4cats.opentelemetry.common

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.sdk.resources.Resource

object Trace4CatsResource {
  def apply(serviceName: String): Resource =
    Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName))
}
