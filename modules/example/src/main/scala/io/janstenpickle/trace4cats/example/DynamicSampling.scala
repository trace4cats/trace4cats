package io.janstenpickle.trace4cats.example

import cats.Applicative
import cats.effect.kernel.{Async, Resource, Temporal}
import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.log.LogSpanCompleter
import io.janstenpickle.trace4cats.model.{SpanKind, TraceProcess}
import io.janstenpickle.trace4cats.sampling.dynamic.http.HttpDynamicSpanSampler

import scala.concurrent.duration._

object DynamicSampling extends IOApp {

  // creates a sampler and binds it to 8080
  // try "curl http:///localhost:8080/trace4cats/config" to see how the sampler is configured
  // then "curl -XPOST -d '{"samplerType": "Always"}' http:///localhost:8080/trace4cats/config" to enable tracing
  // "curl -XPOST http:///localhost:8080/trace4cats/killswitch" to disable tracing (sets "never" sampler)
  // configure rate based sampling like so: "curl -XPOST -d '{"samplerType": "Rate", "bucketSize": 10, "tokenRate": 2.0 }' http:///localhost:8080/trace4cats/config"
  // or probability sampling: "curl -XPOST -d '{"samplerType": "Probabilistic", "probability": 0.5 }' http:///localhost:8080/trace4cats/config"
  def sampler[F[_]: Async]: Resource[F, SpanSampler[F]] = HttpDynamicSpanSampler.create[F]()

  def entryPoint[F[_]: Async]: Resource[F, EntryPoint[F]] =
    for {
      s <- sampler[F]
      c <- Resource.eval(LogSpanCompleter.create[F](TraceProcess("example")))
    } yield EntryPoint[F](s, c)

  def app[F[_]: Temporal](ep: EntryPoint[F]): Stream[F, Unit] = Stream.awakeEvery(10.seconds).evalMap { ts =>
    ep.root("root").flatMap(_.child(ts.toString(), SpanKind.Internal)).use(_ => Applicative[F].unit)
  }

  override def run(args: List[String]): IO[ExitCode] = (for {
    ep <- entryPoint[IO]
    _ <- app[IO](ep).compile.drain.background
  } yield ExitCode.Success).useForever
}
