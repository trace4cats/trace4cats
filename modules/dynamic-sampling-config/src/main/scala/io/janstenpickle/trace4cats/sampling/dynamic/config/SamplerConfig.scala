package io.janstenpickle.trace4cats.sampling.dynamic.config

import cats.kernel.Eq

sealed trait SamplerConfig
object SamplerConfig {
  case object Always extends SamplerConfig
  case object Never extends SamplerConfig
  case class Probabilistic(probability: Double, rootSpansOnly: Boolean = true) extends SamplerConfig
  case class Rate(bucketSize: Int, tokenRate: Double, rootSpansOnly: Boolean = true) extends SamplerConfig

  implicit val eq: Eq[SamplerConfig] = Eq.instance {
    case (Always, Always) => true
    case (Never, Never) => true
    case (Probabilistic(probability1, rootSpansOnly1), Probabilistic(probability2, rootSpansOnly2)) =>
      Eq[Double].eqv(probability1, probability2) && rootSpansOnly1 == rootSpansOnly2
    case (Rate(bucketSize1, tokenRate1, rootSpansOnly1), Rate(bucketSize2, tokenRate2, rootSpansOnly2)) =>
      Eq[Int].eqv(bucketSize1, bucketSize2) && Eq[Double]
        .eqv(tokenRate1, tokenRate2) && rootSpansOnly1 == rootSpansOnly2
    case (_, _) => false
  }
}
