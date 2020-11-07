package io.janstenpickle.trace4cats.strackdriver.oauth

/**
  * Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.nio.file.{Files, Path}
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.regex.Pattern

import cats.syntax.either._
import io.circe.Codec
import io.circe.generic.extras.semiauto._

object GoogleAccountParser {
  case class JsonGoogleServiceAccount(
    `type`: String,
    projectId: String,
    privateKeyId: String,
    privateKey: String,
    clientEmail: String,
    authUri: String
  )

  object JsonGoogleServiceAccount {
    implicit final val codec: Codec[JsonGoogleServiceAccount] = deriveConfiguredCodec
  }

  final def parse(path: Path): Either[Throwable, GoogleServiceAccount] =
    for {
      string <- Either.catchNonFatal(Files.readString(path))
      json <- io.circe.parser.parse(string)
      serviceAccount <- json.as[JsonGoogleServiceAccount]
      gsa <- Either.catchNonFatal {
        val spec = new PKCS8EncodedKeySpec(loadPem(serviceAccount.privateKey))
        val kf = KeyFactory.getInstance("RSA")
        GoogleServiceAccount(
          clientEmail = serviceAccount.clientEmail,
          privateKey = kf.generatePrivate(spec).asInstanceOf[RSAPrivateKey]
        )
      }
    } yield gsa

  final private[this] val privateKeyPattern = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*")

  private def loadPem(pem: String): Array[Byte] = {
    val encoded = privateKeyPattern.matcher(pem).replaceFirst("$1")
    Base64.getMimeDecoder.decode(encoded)
  }
}

case class GoogleServiceAccount(clientEmail: String, privateKey: RSAPrivateKey)
