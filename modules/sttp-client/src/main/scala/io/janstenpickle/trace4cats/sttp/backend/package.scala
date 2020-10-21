package io.janstenpickle.trace4cats.sttp

import sttp.client.Request

package object backend {
  type SttpSpanNamer = Request[_, _] => String
}
