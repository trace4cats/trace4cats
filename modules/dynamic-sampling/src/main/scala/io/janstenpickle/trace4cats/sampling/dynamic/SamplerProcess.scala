package io.janstenpickle.trace4cats.sampling.dynamic

import io.janstenpickle.trace4cats.kernel.SpanSampler

private[dynamic] case class SamplerProcess[F[_]](value: SpanSampler[F], cancel: F[Unit])
