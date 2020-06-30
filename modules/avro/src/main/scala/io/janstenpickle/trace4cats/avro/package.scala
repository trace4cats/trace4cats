package io.janstenpickle.trace4cats

import scala.util.Try

package object avro {
  final val AgentHostEnv = "T4C_AGENT_HOSTNAME"
  final val AgentPortEnv = "T4C_AGENT_PORT"

  final val CollectorHostEnv = "T4C_COLLECTOR_HOST"
  final val CollectorPortEnv = "T4C_COLLECTOR_PORT"

  final val DefaultHostname = "localhost"
  final val DefaultPort = 7777

  def intEnv(key: String): Option[Int] = Option(System.getenv(key)).flatMap(v => Try(v.toInt).toOption)

  def agentHostname: String = Option(System.getenv(AgentHostEnv)).getOrElse(DefaultHostname)
  def agentPort: Int = intEnv(AgentPortEnv).getOrElse(DefaultPort)
}
