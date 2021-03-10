//package io.janstenpickle.trace4cats.example
//
//import cats.effect.Blocker
//import cats.syntax.flatMap._
//import io.janstenpickle.trace4cats.Span
//import io.janstenpickle.trace4cats.`export`.CompleterConfig
//import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
//import io.janstenpickle.trace4cats.kernel.SpanSampler
//import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}
//import zio.interop.catz.CatsApp
//import zio.interop.catz._
//import zio.interop.catz.implicits._
//import zio.{Exit, ExitCode, Task, URIO}
//
//import scala.concurrent.duration._
//
///** This example shows how to send traces to the Avro Agent.
//  *
//  * Note how spans are surfaced as instances of `cats.effect.Resource` so may be flatMapped, however in this
//  * example the `use` method is called explicitly
//  */
//object SimpleZioExample extends CatsApp {
//
//  override def run(args: List[String]): URIO[zio.ZEnv, zio.ExitCode] =
//    (for {
//      blocker <- Blocker[Task]
//      completer <- AvroSpanCompleter.udp[Task](
//        blocker,
//        TraceProcess("test"),
//        config = CompleterConfig(batchTimeout = 50.millis)
//      )
//    } yield completer)
//      .use { completer =>
//        // Spans are surfaced as `cats.effect.Resource`s which form a timed bracket around an executed effect
//        Span.root[Task]("root", SpanKind.Client, SpanSampler.always, completer).use { root =>
//          // do some stuff
//          root
//            .putAll("root-attribute" -> "I am Root", "app-ver" -> "0.0.1") >> root.child("child", SpanKind.Server).use {
//            child =>
//              // do some more stuff
//              for {
//                _ <- child.put("string-attribute", "test")
//                _ <- child.put("int-attribute", 99)
//                _ <- child.put("bool-attribute", true)
//                _ <- child.put("double-attribute", 23.0)
//                _ <- child.setStatus(SpanStatus.Internal("Some error message"))
//              } yield ExitCode.success
//          }
//        }
//
//      }
//      .run
//      .map {
//        case Exit.Success(value) => value
//        case Exit.Failure(_) => ExitCode.failure
//      }
//}
