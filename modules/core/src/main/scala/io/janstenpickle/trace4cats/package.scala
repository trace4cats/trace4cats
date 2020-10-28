package io.janstenpickle

import io.janstenpickle.trace4cats.model.SampleDecision

package object trace4cats {
  private[trace4cats] val b3SampledFlag: Option[String] => SampleDecision =
    _.fold[SampleDecision](SampleDecision.Include) {
      case "1" | "d" | "true" => SampleDecision.Include
      case "0" | "false" => SampleDecision.Drop
      case _ => SampleDecision.Include
    }
}
