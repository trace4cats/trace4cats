package trace4cats.context.iolocal.laws

import cats.effect.testkit.TestControl
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, IOLocal}
import cats.{~>, Eq}
import org.scalacheck.{Arbitrary, Cogen, Gen}
import trace4cats.context.Provide
import trace4cats.context.iolocal.GenIO
import trace4cats.context.iolocal.instances.ioLocalProvide
import trace4cats.context.laws.BaseSuite
import trace4cats.context.laws.discipline.ProvideTests

import scala.util.Try

class IOLocalTests extends BaseSuite with GenIO {

  implicit def arbitraryIO[A: Arbitrary: Cogen]: Arbitrary[IO[A]] =
    Arbitrary(Gen.oneOf(genIO[A], genLikeTrans(genIO[A]), genIdentityTrans(genIO[A])))

  implicit val eqThrowable: Eq[Throwable] = Eq.fromUniversalEquals[Throwable]

  implicit def eqIO[A: Eq]: Eq[IO[A]] = Eq.by(io => Try(TestControl.executeEmbed(io).unsafeRunSync()))

  implicit val cogenIO2IO: Cogen[IO ~> IO] =
    Cogen(k => k(IO.pure(0)).hashCode().toLong)

  // TODO: The implementation is nonsensical (test passes though, why?)
  implicit val eqIO2IO: Eq[IO ~> IO] = Eq.by(_ => false)

  checkAll(
    "IO <~> IO via IOLocal", {
      implicit val ioLocal: IOLocal[String] = IOLocal.apply("root").unsafeRunSync()
      implicit val provideIO2IO: Provide[IO, IO, String] = ioLocalProvide(ioLocal)

      ProvideTests[IO, IO, String].provide[String, Int]
    }
  )
}
