package io.janstenpickle.trace4cats

import cats.data.Kleisli

package object inject {
  type Spanned[F[_], A] = Kleisli[F, Span[F], A]
}
