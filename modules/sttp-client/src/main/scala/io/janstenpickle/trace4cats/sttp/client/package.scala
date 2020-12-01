package io.janstenpickle.trace4cats.sttp

import sttp.client.Request

package object client {
  type SttpSpanNamer = Request[_, _] => String
}
