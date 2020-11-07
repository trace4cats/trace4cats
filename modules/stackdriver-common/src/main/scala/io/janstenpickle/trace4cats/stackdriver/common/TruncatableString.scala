package io.janstenpickle.trace4cats.stackdriver.common

case class TruncatableString private (value: String, truncatedByteCount: Int)
object TruncatableString {
  private final val utf8 = "UTF-8"
  private final val maxLen = 128

  def apply(string: String): TruncatableString = {
    val bytes = string.getBytes(utf8)
    val bytesLength = bytes.length
    if (bytesLength > maxLen) {
      val newString = new String(bytes, 0, maxLen, utf8)
      TruncatableString(newString, bytesLength - newString.getBytes(utf8).length)
    } else
      TruncatableString(string, 0)
  }
}
