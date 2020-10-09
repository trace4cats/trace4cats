package io.janstenpickle.trace4cats.opentelemetry.common

import io.janstenpickle.trace4cats.model.AttributeValue.StringValue
import io.janstenpickle.trace4cats.model.TraceProcess
import io.opentelemetry.common.Attributes
import io.opentelemetry.sdk.resources.Resource

object Trace4CatsResource {
  final val ServiceNameResourceKey = "service.name"

  def apply(process: TraceProcess): Resource =
    Resource.create(
      Attributes
        .newBuilder(
          Trace4CatsReadableAttributes(
            process.attributes + (ServiceNameResourceKey -> StringValue(process.serviceName))
          )
        )
        .build()
    )
}
