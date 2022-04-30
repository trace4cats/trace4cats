package trace4cats

package object kernel {
  type ErrorHandler = PartialFunction[Throwable, HandledError]
}
