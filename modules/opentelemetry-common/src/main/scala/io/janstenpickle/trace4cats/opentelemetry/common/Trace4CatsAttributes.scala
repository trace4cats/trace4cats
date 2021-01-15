package io.janstenpickle.trace4cats.opentelemetry.common

import io.janstenpickle.trace4cats.model.AttributeValue
import io.janstenpickle.trace4cats.model.AttributeValue._
import io.opentelemetry.api.common.{AttributeKey, AttributeType, Attributes, AttributesBuilder}

import java.util
import java.util.function.BiConsumer
import scala.jdk.CollectionConverters._

object Trace4CatsAttributes {

  def apply(map: Map[String, AttributeValue]): Attributes =
    new Attributes {
      override def get[T](key: AttributeKey[T]): T = {
        (key.getType match {
          case AttributeType.STRING =>
            map.get(key.getKey).collect { case StringValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.BOOLEAN =>
            map.get(key.getKey).collect { case BooleanValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.LONG =>
            map.get(key.getKey).collect { case LongValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.DOUBLE =>
            map.get(key.getKey).collect { case DoubleValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.STRING_ARRAY =>
            map.get(key.getKey).collect { case StringList(value) => value.value.toList.asJava.asInstanceOf[T] }
          case AttributeType.BOOLEAN_ARRAY =>
            map.get(key.getKey).collect { case BooleanList(value) => value.value.toList.asJava.asInstanceOf[T] }
          case AttributeType.LONG_ARRAY =>
            map.get(key.getKey).collect { case LongList(value) => value.value.toList.asJava.asInstanceOf[T] }
          case AttributeType.DOUBLE_ARRAY =>
            map.get(key.getKey).collect { case DoubleList(value) => value.value.toList.asJava.asInstanceOf[T] }
        }).get
      }

      override val size: Int = map.size

      override val isEmpty: Boolean = map.isEmpty

      override def forEach(consumer: BiConsumer[AttributeKey[_], AnyRef]): Unit = {
        def safeConsume[A <: AnyRef](key: AttributeKey[A])(attr: A): Unit =
          consumer.accept(key, attr)

        map.foreach { case (k, v) =>
          v match {
            case StringValue(value) => safeConsume(AttributeKey.stringKey(k))(value.value)
            case BooleanValue(value) => safeConsume(AttributeKey.booleanKey(k))(value.value)
            case DoubleValue(value) => safeConsume(AttributeKey.doubleKey(k))(value.value)
            case LongValue(value) => safeConsume(AttributeKey.longKey(k))(value.value)
            case StringList(value) =>
              safeConsume(AttributeKey.stringArrayKey(k))(value.value.toList.asJava)
            case BooleanList(value) =>
              safeConsume(AttributeKey.booleanArrayKey(k))(value.value.map(x => x: java.lang.Boolean).toList.asJava)
            case DoubleList(value) =>
              safeConsume(AttributeKey.doubleArrayKey(k))(value.value.map(x => x: java.lang.Double).toList.asJava)
            case LongList(value) =>
              safeConsume(AttributeKey.longArrayKey(k))(value.value.map(x => x: java.lang.Long).toList.asJava)
          }
        }
      }

      override def asMap: util.Map[AttributeKey[_], AnyRef] = {
        val data = new util.HashMap[AttributeKey[_], AnyRef]()
        forEach { (k, a) =>
          val _ = data.put(k, a)
        }
        java.util.Collections.unmodifiableMap(data)
      }

      override def toBuilder: AttributesBuilder = Attributes.builder.putAll(this)
    }
}
