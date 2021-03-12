package io.janstenpickle.trace4cats.stackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import io.circe.Codec
import io.circe.generic.extras.semiauto._

final case class AccessToken(accessToken: String, tokenType: String, expiresIn: Long)

object AccessToken {
  implicit val codec: Codec[AccessToken] = deriveConfiguredCodec
}
