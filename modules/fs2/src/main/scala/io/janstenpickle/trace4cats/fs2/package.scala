package io.janstenpickle.trace4cats

import cats.data.WriterT
import io.janstenpickle.trace4cats.model.SpanContext
import _root_.fs2.Stream

package object fs2 {
  type EntryPointStream[F[_], A] = WriterT[Stream[F, *], Fs2EntryPoint[F], A]
  type TraceHeadersStream[F[_], A] = WriterT[Stream[F, *], (Fs2EntryPoint[F], Map[String, String]), A]
  type TracedStream[F[_], A] = WriterT[Stream[F, *], (Fs2EntryPoint[F], SpanContext), A]
}
