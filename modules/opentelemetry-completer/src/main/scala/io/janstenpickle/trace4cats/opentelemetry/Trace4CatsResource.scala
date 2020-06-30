package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.TraceProcess
import io.opentelemetry.common.AttributeValue
import io.opentelemetry.sdk.resources.Resource

object Trace4CatsResource {
  final val ServiceNameResourceKey = "service-name"

  def apply(process: TraceProcess): Resource = {
    val attrs = toAttributes(process.attributes)
    attrs.put(ServiceNameResourceKey, AttributeValue.stringAttributeValue(process.serviceName))

    Resource.create(attrs)
  }
}
