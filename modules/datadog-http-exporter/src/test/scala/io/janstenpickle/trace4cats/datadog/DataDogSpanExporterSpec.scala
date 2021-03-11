package io.janstenpickle.trace4cats.datadog

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class DataDogSpanExporterSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("DataDogSpanExporter")

  it should "send spans to datadog agent without error" in forAll { batch: Batch[Chunk] =>
    assertResult(())(DataDogSpanExporter.blazeClient[IO, Chunk]().use(_.exportBatch(batch)).unsafeRunSync())
  }
}
