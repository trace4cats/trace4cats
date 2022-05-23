package trace4cats

package object syntax {
  final implicit class TraceOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def span(name: SpanName)(implicit T: Trace[F]): F[A] = T.span(name)(fa)
    def span(name: SpanName, kind: SpanKind)(implicit T: Trace[F]): F[A] = T.span(name, kind)(fa)
  }
}
