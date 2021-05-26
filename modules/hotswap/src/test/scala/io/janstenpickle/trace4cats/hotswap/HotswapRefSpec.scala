package io.janstenpickle.trace4cats.hotswap

import cats.effect.IO
import cats.effect.kernel.{Ref, Resource}
import cats.effect.testkit.TestInstances
import cats.effect.unsafe.IORuntime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.syntax.all._

import scala.concurrent.duration._

class HotswapRefSpec extends AnyFlatSpec with Matchers with TestInstances {
  implicit val runtime: IORuntime = IORuntime.global

  val ref: Resource[IO, Ref[IO, List[String]]] = Resource.eval(Ref.of[IO, List[String]](List.empty[String]))

  behavior.of("HotswapRef")

  it should "swap resources" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      hotswap <- HotswapRef[IO, Ref[IO, List[String]]](Resource.pure(ref0))
      _ <- Resource.eval(hotswap.get.use(_.update("test0" :: _)))
      _ <- Resource.eval(hotswap.swap(Resource.pure(ref1)))
      _ <- Resource.eval(hotswap.get.use(_.update("test1" :: _)))
    } yield (ref0.get, ref1.get)).use(_.tupled)

    val res = test.unsafeRunSync()

    // each completer has been used
    res._1.size should be(1)
    res._2.size should be(1)
    // completion isn't blocked by hotswap
    res._1.head should be("test0")
    res._2.head should be("test1")
  }

  it should "block on swap while handles to previous resource are held" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      releaseSignal <- Resource.eval(Ref.of[IO, Boolean](false))
      hotswap <- HotswapRef[IO, Ref[IO, List[String]]](Resource.pure(ref0))
      _ <- Resource.eval(
        hotswap.get.use(_.update("test0" :: _) >> IO.sleep(2.seconds) >> releaseSignal.set(true)).start
      )
      _ <- Resource.eval(hotswap.swap(Resource.pure(ref1)).start)
      _ <- Resource.eval(hotswap.get.use(_.update("test1" :: _)))
    } yield (ref0.get, ref1.get, releaseSignal.get)).use(_.tupled)

    val res = test.unsafeRunSync()

    // each completer has been used
    res._1.size should be(1)
    res._2.size should be(1)
    // completion isn't blocked by hotswap
    res._1.head should be("test0")
    res._2.head should be("test1")
    // original resource isn't yet released
    res._3 should be(false)
  }

  it should "block on further swaps while one is still in progress" in {
    val test = (for {
      ref0 <- ref
      ref1 <- ref
      ref2 <- ref
      swapSignal <- Resource.eval(Ref.of[IO, Boolean](false))
      hotswap <- HotswapRef[IO, Ref[IO, List[String]]](Resource.pure(ref0))
      _ <- Resource.eval(hotswap.get.use(_.update("test0" :: _) >> IO.sleep(10.seconds)).start)
      _ <- Resource.eval(hotswap.swap(Resource.pure(ref1)).start)
      _ <- Resource.eval(hotswap.get.use(_.update("test1" :: _)))
      _ <- Resource.eval((hotswap.swap(Resource.pure(ref2)) >> swapSignal.set(true)).start)
      _ <- Resource.eval(hotswap.get.use(_.update("test2" :: _)))
    } yield (ref0.get, ref1.get, ref2.get, swapSignal.get)).use(_.tupled)

    val res = test.unsafeRunSync()

    // each completer has been used
    res._1.size should be(1)
    res._2.size should be(2)
    res._3.size should be(0)
    // completion isn't blocked by hotswap
    res._1.head should be("test0")
    res._2 should contain theSameElementsAs List("test1", "test2")
    // original resource isn't yet released
    res._4 should be(false)
  }

  it should "not block on repeated swaps while resource is not in use" in {

    val test = (for {
      hotswap <- HotswapRef[IO, Ref[IO, List[String]]](ref)
      _ <- Resource.eval(hotswap.swap(ref).replicateA(100))
    } yield ()).use(_ => IO(true))

    val res = test.unsafeRunSync()
    assert(res)
  }

  it should "use a resource repeatedly" in {
    val test = (for {
      ref0 <- ref
      hotswap <- HotswapRef[IO, Ref[IO, List[String]]](Resource.pure(ref0))
      _ <- Resource.eval(hotswap.get.use(_.update("" :: _)).replicateA(1000))
    } yield ref0.get).use(identity)

    val res = test.unsafeRunSync()

    // each completer has been used
    res.size should be(1000)
  }
}
