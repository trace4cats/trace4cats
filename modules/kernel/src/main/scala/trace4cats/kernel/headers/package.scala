package trace4cats.kernel

import trace4cats.model.SampleDecision

package object headers {
  // See: https://github.com/openzipkin/b3-propagation/blob/master/README.md#sampling-state-1
  private[headers] val b3SampledFlag: Option[String] => SampleDecision =
    _.fold[SampleDecision](SampleDecision.Include) {
      case "1" | "d" | "true" => SampleDecision.Include
      case "0" | "false" => SampleDecision.Drop
      case _ => SampleDecision.Include
    }
}
