package io.janstenpickle.trace4cats.hotswap

import cats.effect.IO
import cats.effect.kernel.{Ref, Resource}
import cats.effect.testkit.TestInstances
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HotswapRefConstructorSpec extends AnyFlatSpec with Matchers with TestInstances {
  implicit val runtime: IORuntime = IORuntime.global

  behavior.of("HotswapRefConstructor")

  it should "construct new resources" in {
    val test = (for {
      ref <- Resource.eval(Ref.of[IO, List[String]](List.empty[String]))
      resource = (s: String) => Resource.eval(ref.update(s :: _))
      hotswap <- HotswapRefConstructor[IO, String, Unit]("first")(resource)
      _ <- Resource.eval(0.to(1000).toList.traverse(x => hotswap.swapWith(x.toString)))
    } yield ref.get).use(identity)

    val res = test.unsafeRunSync()

    res should contain theSameElementsAs "first" :: 0.to(1000).map(_.toString).toList
  }

  it should "access the current input" in {
    val test = (for {
      hotswap <- HotswapRefConstructor[IO, String, Unit]("first")(_ => Resource.unit[IO])
      i0 <- Resource.eval(hotswap.accessI.use(IO(_)))
      _ <- Resource.eval(hotswap.swapWith("second"))
      i1 <- Resource.eval(hotswap.accessI.use(IO(_)))
    } yield (IO(i0), IO(i1))).use(_.tupled)

    val res = test.unsafeRunSync()

    res._1 should be("first")
    res._2 should be("second")
  }
}
