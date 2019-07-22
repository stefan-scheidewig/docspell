package docspell.restserver.auth

import org.http4s._
import org.http4s.util._
import docspell.backend.auth._
import docspell.common.AccountId
import docspell.restserver.Config

case class CookieData(auth: AuthToken) {
  def accountId: AccountId = auth.account
  def asString: String = auth.asString

  def asCookie(cfg: Config): ResponseCookie = {
    val domain = "" //cfg.baseUrl.hostAndPort
    val sec = false //cfg.baseUrl.protocol.exists(_.endsWith("s"))
    ResponseCookie(CookieData.cookieName, asString, domain = Some(domain), path = Some("/api/v1"), httpOnly = true, secure = sec)
  }
}
object CookieData {
  val cookieName = "docspell_auth"
  val headerName = "X-Docspell-Auth"

  def authenticator[F[_]](r: Request[F]): Either[String, String] =
    fromCookie(r) orElse fromHeader(r)

  def fromCookie[F[_]](req: Request[F]): Either[String, String] = {
    for {
      header   <- headers.Cookie.from(req.headers).toRight("Cookie parsing error")
      cookie   <- header.values.toList.find(_.name == cookieName).toRight("Couldn't find the authcookie")
    } yield cookie.content
  }

  def fromHeader[F[_]](req: Request[F]): Either[String, String] = {
    req.headers.get(CaseInsensitiveString(headerName)).map(_.value).toRight("Couldn't find an authenticator")
  }
}
