package io.janstenpickle.trace4cats.sttp

import sttp.client3.Request

package object client3 {
  type SttpSpanNamer = Request[_, _] => String
}
