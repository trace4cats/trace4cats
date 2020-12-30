package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.server.Instances._
import io.janstenpickle.trace4cats.http4s.server.syntax._

class ResourceKleisliServerSyntaxSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, Span[IO], *]](
      λ[IO ~> Id](_.unsafeRunSync()),
      λ[Kleisli[IO, Span[IO], *] ~> IO](ga => Span.noop[IO].use(ga.run)),
      (routes, filter, ep) => routes.traced(Http4sResourceKleislis.fromHeaders(requestFilter = filter)(ep.toKleisli)),
      (app, filter, ep) => app.traced(Http4sResourceKleislis.fromHeaders(requestFilter = filter)(ep.toKleisli))
    )
