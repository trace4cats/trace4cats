package trace4cats.context

object syntax {

  implicit class LocalOps[F[_], A](val fa: F[A]) extends AnyVal {

    def local[E](f: E => E)(implicit local: Local[F, E]): F[A] =
      local.local(fa)(f)

    def scope[E](e: E)(implicit local: Local[F, E]): F[A] =
      local.scope(fa)(e)

  }

  implicit class ProvideOps[F[_], A](val fa: F[A]) extends AnyVal {

    def provide[Low[_], E](e: E)(implicit provide: Provide[Low, F, E]): Low[A] =
      provide.provide(fa)(e)

  }

}
