package io.janstenpickle.trace4cats

package object jaeger {
  val excludedTagKeys: Set[String] = Set("ip")
}
