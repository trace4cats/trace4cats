package io.janstenpickle.trace4cats.strackdriver.project

trait ProjectIdProvider[F[_]] {
  def projectId: F[String]
}

object ProjectIdProvider {
  def apply[F[_]](implicit projectIdProvider: ProjectIdProvider[F]): ProjectIdProvider[F] = projectIdProvider
}
