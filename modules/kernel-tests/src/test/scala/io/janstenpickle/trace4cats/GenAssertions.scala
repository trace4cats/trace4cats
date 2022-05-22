package io.janstenpickle.trace4cats

import cats.Eq
import cats.effect.IO
import cats.syntax.parallel._
import org.scalatest.{Assertion, Assertions}

private[trace4cats] object GenAssertions extends Assertions {

  def assertAllDistinct[A: Eq](generate: IO[A]): IO[Assertion] =
    generate
      .parReplicateA(16) // don't choose too high or the test might sometimes fail by chance (birthday paradoxon)
      .map { ids =>
        assert(Eq.eqv(ids.distinct, ids), s"got only ${ids.distinct.size} distinct IDs instead of 16")
      }
}
