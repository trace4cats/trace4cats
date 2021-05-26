package io.janstenpickle.trace4cats.hotswap

import cats.effect.IO
import cats.effect.kernel.{Ref, Resource}
import cats.effect.testkit.TestInstances
import cats.effect.unsafe.IORuntime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.syntax.traverse._

class HotswapConstructorSpec extends AnyFlatSpec with Matchers with TestInstances {
  implicit val runtime: IORuntime = IORuntime.global

  behavior.of("HotswapConstructor")

  it should "construct new resources" in {
    val test = (for {
      ref <- Resource.eval(Ref.of[IO, List[String]](List.empty[String]))
      resource = (s: String) => Resource.eval(ref.update(s :: _))
      hotswap <- HotswapConstructor[IO, String, Unit]("first")(resource)
      _ <- Resource.eval(0.to(1000).toList.traverse(x => hotswap.swap(x.toString)))
    } yield ref.get).use(identity)

    val res = test.unsafeRunSync()

    // each completer has been used
    res should contain theSameElementsAs "first" :: 0.to(1000).map(_.toString).toList
  }
}
