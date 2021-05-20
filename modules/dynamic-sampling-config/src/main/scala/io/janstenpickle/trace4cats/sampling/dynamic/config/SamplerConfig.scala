package io.janstenpickle.trace4cats.sampling.dynamic.config

import cats.derived.semiauto
import cats.kernel.Eq

sealed trait SamplerConfig
object SamplerConfig {
  case object Always extends SamplerConfig
  case object Never extends SamplerConfig
  case class Probabilistic(probability: Double, rootSpansOnly: Boolean = true) extends SamplerConfig
  case class Rate(bucketSize: Int, tokenRate: Double, rootSpansOnly: Boolean = true) extends SamplerConfig

  implicit val eq: Eq[SamplerConfig] = semiauto.eq
}
