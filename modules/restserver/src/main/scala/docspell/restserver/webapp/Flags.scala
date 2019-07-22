package docspell.restserver.webapp

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import docspell.common.LenientUri
import docspell.restserver.Config
import docspell.backend.signup.{Config => SignupConfig}
import yamusca.imports._
import yamusca.implicits._

case class Flags(appName: String, baseUrl: LenientUri, signupMode: SignupConfig.Mode)

object Flags {
  def apply(cfg: Config): Flags =
    Flags(cfg.appName, cfg.baseUrl, cfg.backend.signup.mode)

  implicit val jsonEncoder: Encoder[Flags] =
    deriveEncoder[Flags]

  implicit def yamuscaSignupModeConverter: ValueConverter[SignupConfig.Mode] =
    ValueConverter.of(m => Value.fromString(m.name))
  implicit def yamuscaUriConverter: ValueConverter[LenientUri] =
    ValueConverter.of(uri => Value.fromString(uri.asString))
  implicit def yamuscaValueConverter: ValueConverter[Flags] =
    ValueConverter.deriveConverter[Flags]
}
