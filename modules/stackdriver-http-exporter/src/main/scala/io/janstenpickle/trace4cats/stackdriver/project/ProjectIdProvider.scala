package io.janstenpickle.trace4cats.stackdriver.project

trait ProjectIdProvider[F[_]] {
  def projectId: F[String]
}

object ProjectIdProvider {
  def apply[F[_]](implicit projectIdProvider: ProjectIdProvider[F]): ProjectIdProvider[F] = projectIdProvider
}
