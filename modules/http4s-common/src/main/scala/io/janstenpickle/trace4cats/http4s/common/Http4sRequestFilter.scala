package io.janstenpickle.trace4cats.http4s.common

import org.http4s.Uri
import org.http4s.dsl.Http4sDsl

object Http4sRequestFilter {
  private object dsl extends Http4sDsl[AnyK]
  import dsl._

  val allowAll: Http4sRequestFilter = { case _ =>
    true
  }

  val kubernetes: Http4sRequestFilter = {
    case GET -> Root / "readiness" => false
    case GET -> Root / "liveness" => false
    case GET -> Root / "healthcheck" => false
  }

  val prometheus: Http4sRequestFilter = { case GET -> Root / "metrics" =>
    false
  }

  val kubernetesPrometheus: Http4sRequestFilter = kubernetes.orElse(prometheus)

  def fullPaths(first: String, others: String*): Http4sRequestFilter = {
    val paths: Set[String] = Set(first) ++ others

    { case req =>
      !paths.contains(Uri.decode(req.uri.path.toString))
    }
  }
}
