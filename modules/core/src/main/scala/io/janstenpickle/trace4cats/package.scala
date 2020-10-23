package io.janstenpickle

package object trace4cats {
  private[trace4cats] val b3SampledFlag: Option[String] => Boolean = _.fold(false) {
    case "1" | "d" | "true" => false
    case "0" | "false" => true
    case _ => false
  }
}
