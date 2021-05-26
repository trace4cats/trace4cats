package io.janstenpickle.trace4cats.hotswap

import cats.effect.IO
import cats.effect.kernel.{Ref, Resource}
import cats.effect.testkit.TestInstances
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConditionalHotswapRefConstructorSpec extends AnyFlatSpec with Matchers with TestInstances {
  implicit val runtime: IORuntime = IORuntime.global

  behavior.of("ConditionalHotswapRefConstructor")

  it should "construct new resources when the input is different" in {
    val test = (for {
      ref <- Resource.eval(Ref.of[IO, List[String]](List.empty[String]))
      resource = (s: String) => Resource.eval(ref.update(s :: _))
      hotswap <- ConditionalHotswapRefConstructor[IO, String, Unit]("first")(resource)
      _ <- Resource.eval(0.to(1000).toList.traverse(x => hotswap.maybeSwapWith(x.toString)))
    } yield ref.get).use(identity)

    val res = test.unsafeRunSync()

    res should contain theSameElementsAs "first" :: 0.to(1000).map(_.toString).toList
  }

  it should "not construct new resources when input is the same" in {
    val test = (for {
      ref <- Resource.eval(Ref.of[IO, List[String]](List.empty[String]))
      resource = (s: String) => Resource.eval(ref.update(s :: _))
      hotswap <- ConditionalHotswapRefConstructor[IO, String, Unit]("first")(resource)
      _ <- Resource.eval(hotswap.maybeSwapWith("first").replicateA(1000))
    } yield ref.get).use(identity)

    val res = test.unsafeRunSync()

    res should contain theSameElementsAs List("first")
  }
}
