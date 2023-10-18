package trace4cats.context

import cats.{~>, Monad}
import cats.syntax.all._
import trace4cats.optics.Getter

trait Ask[F[_], R] extends ContextRoot { self =>
  def F: Monad[F]

  def ask[R1 >: R]: F[R1]

  def access[A](f: R => A): F[A] = F.map(ask)(f)
  def accessF[A](f: R => F[A]): F[A] = F.flatMap(ask)(f)

  def zoom[R1](g: Getter[R, R1]): Ask[F, R1] = Ask.make(self.access(g.get))(self.F)

  def mapK[G[_]: Monad](fk: F ~> G): Ask[G, R] = Ask.make(fk(self.ask[R]))

}

object Ask {

  def apply[F[_], R](implicit ev: Ask[F, R]): Ask[F, R] = ev

  def make[F[_]: Monad, A](f: F[A]): Ask[F, A] = new Ask[F, A] {
    override def F: Monad[F] = Monad[F]

    def ask[E2 >: A]: F[E2] = f.widen

  }

}
