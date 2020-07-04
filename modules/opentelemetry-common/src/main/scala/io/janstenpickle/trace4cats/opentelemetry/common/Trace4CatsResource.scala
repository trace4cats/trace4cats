package io.janstenpickle.trace4cats.opentelemetry.common

import io.janstenpickle.trace4cats.model.TraceProcess
import io.opentelemetry.common.{AttributeValue, Attributes}
import io.opentelemetry.sdk.resources.Resource

object Trace4CatsResource {
  final val ServiceNameResourceKey = "service-name"

  def apply(process: TraceProcess): Resource = {
    val attrs = toAttributes[Attributes.Builder](
      process.attributes,
      Attributes.newBuilder(),
      (builder, k, v) => builder.setAttribute(k, v)
    ).setAttribute(ServiceNameResourceKey, AttributeValue.stringAttributeValue(process.serviceName))

    Resource.create(attrs.build())
  }
}
