package io.janstenpickle.trace4cats.opentelemetry.common

import io.opentelemetry.common.{AttributeValue, ReadableAttributes, ReadableKeyValuePairs}

object Trace4CatsReadableAttributes {

  def apply(map: Map[String, AttributeValue]): ReadableAttributes = new ReadableAttributes {
    override def size(): Int = map.size

    override def isEmpty: Boolean = map.isEmpty

    override def forEach(consumer: ReadableKeyValuePairs.KeyValueConsumer[AttributeValue]): Unit =
      map.foreach { case (k, v) => consumer.consume(k, v) }

    override def get(key: String): AttributeValue = map.get(key).orNull
  }
}
