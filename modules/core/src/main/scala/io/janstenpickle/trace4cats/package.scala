package io.janstenpickle

import io.janstenpickle.trace4cats.model.SampleDecision

package object trace4cats {
  // See: https://github.com/openzipkin/b3-propagation/blob/master/README.md#sampling-state-1
  private[trace4cats] val b3SampledFlag: Option[String] => SampleDecision =
    _.fold[SampleDecision](SampleDecision.Include) {
      case "1" | "d" | "true" => SampleDecision.Include
      case "0" | "false" => SampleDecision.Drop
      case _ => SampleDecision.Include
    }
}
