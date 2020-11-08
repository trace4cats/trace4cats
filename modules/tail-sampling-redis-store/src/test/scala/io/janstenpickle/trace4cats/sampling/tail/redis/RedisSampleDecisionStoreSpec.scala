package io.janstenpickle.trace4cats.sampling.tail.redis

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.syntax.flatMap._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.model.{SampleDecision, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import redis.embedded.RedisServer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class RedisSampleDecisionStoreSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val redisPort = 6379

  it should "store a decision in redis and retrieve it" in {
    val redisServer = new RedisServer(redisPort)
    redisServer.start()

    forAll { (traceId: TraceId, sampleDecision: SampleDecision, keyPrefix: Short) =>
      (for {
        store0 <- RedisSampleDecisionStore[IO]("localhost", redisPort, keyPrefix, 5.minutes, None)
        store1 <- RedisSampleDecisionStore[IO]("localhost", redisPort, keyPrefix, 5.minutes, None)
        _ <- Resource.liftF(store0.storeDecision(traceId, sampleDecision))
        res <- Resource.liftF(store1.getDecision(traceId))
      } yield res).use(res => IO(res should be(Some(sampleDecision)))).unsafeRunSync()

    }

    redisServer.stop()
  }

  it should "store a decision in redis and retrieve it from local cache" in {
    val redisServer = new RedisServer(redisPort)
    redisServer.start()

    forAll { (traceId: TraceId, sampleDecision: SampleDecision, keyPrefix: Short) =>
      RedisSampleDecisionStore[IO]("localhost", redisPort, keyPrefix, 5.minutes, None)
        .use { store =>
          store
            .storeDecision(traceId, sampleDecision) >> store.getDecision(traceId).map(_ should be(Some(sampleDecision)))
        }
        .unsafeRunSync()

    }

    redisServer.stop()
  }

  it should "store a batch of decisions in redis and retrieve it" in {
    val redisServer = new RedisServer(redisPort)
    redisServer.start()

    forAll { (decisions: Map[TraceId, SampleDecision], keyPrefix: Short) =>
      (for {
        store0 <- RedisSampleDecisionStore[IO]("localhost", redisPort, keyPrefix, 5.minutes, None)
        store1 <- RedisSampleDecisionStore[IO]("localhost", redisPort, keyPrefix, 5.minutes, None)
        _ <- Resource.liftF(store0.storeDecisions(decisions))
        res <- Resource.liftF(store1.batch(decisions.keySet))
      } yield res).use(res => IO(res should contain theSameElementsAs decisions)).unsafeRunSync()

    }

    redisServer.stop()
  }

  it should "store a batch of decisions in redis and retrieve it from local cache" in {
    val redisServer = new RedisServer(redisPort)
    redisServer.start()

    forAll { (decisions: Map[TraceId, SampleDecision], keyPrefix: Short) =>
      RedisSampleDecisionStore[IO]("localhost", redisPort, keyPrefix, 5.minutes, None)
        .use { store =>
          store
            .storeDecisions(decisions) >> store
            .batch(decisions.keySet)
            .map(_ should contain theSameElementsAs decisions)
        }
        .unsafeRunSync()

    }

    redisServer.stop()
  }
}
