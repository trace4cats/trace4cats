package io.janstenpickle.trace4cats.model

import cats.effect.IO
import cats.kernel.Eq
import cats.syntax.parallel._
import org.scalatest.{Assertion, Assertions}

private[model] object GenAssertions extends Assertions {

  def assertAllDistinct[A: Eq](generate: IO[A]): IO[Assertion] =
    generate
      .parReplicateA(16) // don't choose too high or the test might sometimes fail by chance (birthday paradoxon)
      .map { ids =>
        assert(Eq.eqv(ids.distinct, ids), s"got only ${ids.distinct.size} distinct IDs instead of 16")
      }
}
