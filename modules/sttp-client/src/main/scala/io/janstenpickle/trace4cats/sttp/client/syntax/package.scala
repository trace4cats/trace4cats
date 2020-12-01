package io.janstenpickle.trace4cats.sttp.client

package object syntax extends BackendSyntax {

  /** Use these aliases for parameterizing backends without streaming support, e.g.
    * `Backend[IO, INothing, INothingT]`. Otherwise syntax extensions may not work.
    */
  type INothing <: Nothing
  type INothingT[_] <: Nothing
}
