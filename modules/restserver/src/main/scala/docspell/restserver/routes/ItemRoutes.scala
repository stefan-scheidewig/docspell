package docspell.restserver.routes

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._

import docspell.backend.BackendApp
import docspell.backend.auth.AuthToken
import docspell.backend.ops.OFulltext
import docspell.backend.ops.OItemSearch.Batch
import docspell.common.syntax.all._
import docspell.common.{Ident, ItemState}
import docspell.restapi.model._
import docspell.restserver.Config
import docspell.restserver.conv.Conversions
import docspell.restserver.http4s.BinaryUtil
import docspell.restserver.http4s.Responses

import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.log4s._

object ItemRoutes {
  private[this] val logger = getLogger

  def apply[F[_]: Effect](
      cfg: Config,
      backend: BackendApp[F],
      user: AuthToken
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of {
      case POST -> Root / "convertallpdfs" =>
        for {
          res <-
            backend.item.convertAllPdf(user.account.collective.some, user.account, true)
          resp <- Ok(Conversions.basicResult(res, "Task submitted"))
        } yield resp

      case req @ POST -> Root / "search" =>
        for {
          mask <- req.as[ItemSearch]
          _    <- logger.ftrace(s"Got search mask: $mask")
          query = Conversions.mkQuery(mask, user.account)
          _ <- logger.ftrace(s"Running query: $query")
          resp <- mask.fullText match {
            case Some(fq) if cfg.fullTextSearch.enabled =>
              for {
                items <- backend.fulltext.findItems(cfg.maxNoteLength)(
                  query,
                  OFulltext.FtsInput(fq),
                  Batch(mask.offset, mask.limit).restrictLimitTo(cfg.maxItemPageSize)
                )
                ok <- Ok(Conversions.mkItemListFts(items))
              } yield ok
            case _ =>
              for {
                items <- backend.itemSearch.findItems(cfg.maxNoteLength)(
                  query,
                  Batch(mask.offset, mask.limit).restrictLimitTo(cfg.maxItemPageSize)
                )
                ok <- Ok(Conversions.mkItemList(items))
              } yield ok
          }
        } yield resp

      case req @ POST -> Root / "searchWithTags" =>
        for {
          mask <- req.as[ItemSearch]
          _    <- logger.ftrace(s"Got search mask: $mask")
          query = Conversions.mkQuery(mask, user.account)
          _ <- logger.ftrace(s"Running query: $query")
          resp <- mask.fullText match {
            case Some(fq) if cfg.fullTextSearch.enabled =>
              for {
                items <- backend.fulltext.findItemsWithTags(cfg.maxNoteLength)(
                  query,
                  OFulltext.FtsInput(fq),
                  Batch(mask.offset, mask.limit).restrictLimitTo(cfg.maxItemPageSize)
                )
                ok <- Ok(Conversions.mkItemListWithTagsFts(items))
              } yield ok
            case _ =>
              for {
                items <- backend.itemSearch.findItemsWithTags(cfg.maxNoteLength)(
                  query,
                  Batch(mask.offset, mask.limit).restrictLimitTo(cfg.maxItemPageSize)
                )
                ok <- Ok(Conversions.mkItemListWithTags(items))
              } yield ok
          }
        } yield resp

      case req @ POST -> Root / "searchIndex" =>
        for {
          mask <- req.as[ItemFtsSearch]
          resp <- mask.query match {
            case q if q.length > 1 =>
              val ftsIn = OFulltext.FtsInput(q)
              for {
                items <- backend.fulltext.findIndexOnly(cfg.maxNoteLength)(
                  ftsIn,
                  user.account,
                  Batch(mask.offset, mask.limit).restrictLimitTo(cfg.maxItemPageSize)
                )
                ok <- Ok(Conversions.mkItemListWithTagsFtsPlain(items))
              } yield ok

            case _ =>
              BadRequest(BasicResult(false, "Query string too short"))
          }
        } yield resp

      case GET -> Root / Ident(id) =>
        for {
          item <- backend.itemSearch.findItem(id, user.account.collective)
          result = item.map(Conversions.mkItemDetail)
          resp <-
            result
              .map(r => Ok(r))
              .getOrElse(NotFound(BasicResult(false, "Not found.")))
        } yield resp

      case POST -> Root / Ident(id) / "confirm" =>
        for {
          res  <- backend.item.setState(id, ItemState.Confirmed, user.account.collective)
          resp <- Ok(Conversions.basicResult(res, "Item data confirmed"))
        } yield resp

      case POST -> Root / Ident(id) / "unconfirm" =>
        for {
          res  <- backend.item.setState(id, ItemState.Created, user.account.collective)
          resp <- Ok(Conversions.basicResult(res, "Item back to created."))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "tags" =>
        for {
          tags <- req.as[ReferenceList].map(_.items)
          res  <- backend.item.setTags(id, tags.map(_.id), user.account.collective)
          resp <- Ok(Conversions.basicResult(res, "Tags updated"))
        } yield resp

      case req @ POST -> Root / Ident(id) / "tags" =>
        for {
          data <- req.as[Tag]
          rtag <- Conversions.newTag(data, user.account.collective)
          res  <- backend.item.addNewTag(id, rtag)
          resp <- Ok(Conversions.basicResult(res, "Tag added."))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "taglink" =>
        for {
          tags <- req.as[StringList]
          res  <- backend.item.linkTags(id, tags.items, user.account.collective)
          resp <- Ok(Conversions.basicResult(res, "Tags linked"))
        } yield resp

      case req @ POST -> Root / Ident(id) / "tagtoggle" =>
        for {
          tags <- req.as[StringList]
          res  <- backend.item.toggleTags(id, tags.items, user.account.collective)
          resp <- Ok(Conversions.basicResult(res, "Tags linked"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "direction" =>
        for {
          dir <- req.as[DirectionValue]
          res <- backend.item.setDirection(
            NonEmptyList.of(id),
            dir.direction,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Direction updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "folder" =>
        for {
          idref <- req.as[OptionalId]
          res   <- backend.item.setFolder(id, idref.id, user.account.collective)
          resp  <- Ok(Conversions.basicResult(res, "Folder updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "corrOrg" =>
        for {
          idref <- req.as[OptionalId]
          res <- backend.item.setCorrOrg(
            NonEmptyList.of(id),
            idref.id,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Correspondent organization updated"))
        } yield resp

      case req @ POST -> Root / Ident(id) / "corrOrg" =>
        for {
          data <- req.as[Organization]
          org  <- Conversions.newOrg(data, user.account.collective)
          res  <- backend.item.addCorrOrg(id, org)
          resp <- Ok(Conversions.basicResult(res, "Correspondent organization updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "corrPerson" =>
        for {
          idref <- req.as[OptionalId]
          res <- backend.item.setCorrPerson(
            NonEmptyList.of(id),
            idref.id,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Correspondent person updated"))
        } yield resp

      case req @ POST -> Root / Ident(id) / "corrPerson" =>
        for {
          data <- req.as[Person]
          pers <- Conversions.newPerson(data, user.account.collective)
          res  <- backend.item.addCorrPerson(id, pers)
          resp <- Ok(Conversions.basicResult(res, "Correspondent person updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "concPerson" =>
        for {
          idref <- req.as[OptionalId]
          res <- backend.item.setConcPerson(
            NonEmptyList.of(id),
            idref.id,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Concerned person updated"))
        } yield resp

      case req @ POST -> Root / Ident(id) / "concPerson" =>
        for {
          data <- req.as[Person]
          pers <- Conversions.newPerson(data, user.account.collective)
          res  <- backend.item.addConcPerson(id, pers)
          resp <- Ok(Conversions.basicResult(res, "Concerned person updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "concEquipment" =>
        for {
          idref <- req.as[OptionalId]
          res <- backend.item.setConcEquip(
            NonEmptyList.of(id),
            idref.id,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Concerned equipment updated"))
        } yield resp

      case req @ POST -> Root / Ident(id) / "concEquipment" =>
        for {
          data  <- req.as[Equipment]
          equip <- Conversions.newEquipment(data, user.account.collective)
          res   <- backend.item.addConcEquip(id, equip)
          resp  <- Ok(Conversions.basicResult(res, "Concerned equipment updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "notes" =>
        for {
          text <- req.as[OptionalText]
          res  <- backend.item.setNotes(id, text.text.notEmpty, user.account.collective)
          resp <- Ok(Conversions.basicResult(res, "Notes updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "name" =>
        for {
          text <- req.as[OptionalText]
          res <- backend.item.setName(
            id,
            text.text.notEmpty.getOrElse(""),
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Name updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "duedate" =>
        for {
          date <- req.as[OptionalDate]
          _    <- logger.fdebug(s"Setting item due date to ${date.date}")
          res <- backend.item.setItemDueDate(
            NonEmptyList.of(id),
            date.date,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Item due date updated"))
        } yield resp

      case req @ PUT -> Root / Ident(id) / "date" =>
        for {
          date <- req.as[OptionalDate]
          _    <- logger.fdebug(s"Setting item date to ${date.date}")
          res <- backend.item.setItemDate(
            NonEmptyList.of(id),
            date.date,
            user.account.collective
          )
          resp <- Ok(Conversions.basicResult(res, "Item date updated"))
        } yield resp

      case GET -> Root / Ident(id) / "proposals" =>
        for {
          ml <- backend.item.getProposals(id, user.account.collective)
          ip = Conversions.mkItemProposals(ml)
          resp <- Ok(ip)
        } yield resp

      case req @ POST -> Root / Ident(id) / "attachment" / "movebefore" =>
        for {
          data <- req.as[MoveAttachment]
          _    <- logger.fdebug(s"Move item (${id.id}) attachment $data")
          res  <- backend.item.moveAttachmentBefore(id, data.source, data.target)
          resp <- Ok(Conversions.basicResult(res, "Attachment moved."))
        } yield resp

      case req @ GET -> Root / Ident(id) / "preview" =>
        for {
          preview <- backend.itemSearch.findItemPreview(id, user.account.collective)
          inm     = req.headers.get(`If-None-Match`).flatMap(_.tags)
          matches = BinaryUtil.matchETag(preview.map(_.meta), inm)
          resp <-
            preview
              .map { data =>
                if (matches) BinaryUtil.withResponseHeaders(dsl, NotModified())(data)
                else BinaryUtil.makeByteResp(dsl)(data).map(Responses.noCache)
              }
              .getOrElse(NotFound(BasicResult(false, "Not found")))
        } yield resp

      case HEAD -> Root / Ident(id) / "preview" =>
        for {
          preview <- backend.itemSearch.findItemPreview(id, user.account.collective)
          resp <-
            preview
              .map(data => BinaryUtil.withResponseHeaders(dsl, Ok())(data))
              .getOrElse(NotFound(BasicResult(false, "Not found")))
        } yield resp

      case req @ POST -> Root / Ident(id) / "reprocess" =>
        for {
          data <- req.as[IdList]
          ids = data.ids.flatMap(s => Ident.fromString(s).toOption)
          _    <- logger.fdebug(s"Re-process item ${id.id}")
          res  <- backend.item.reprocess(id, ids, user.account, true)
          resp <- Ok(Conversions.basicResult(res, "Re-process task submitted."))
        } yield resp

      case DELETE -> Root / Ident(id) =>
        for {
          n <- backend.item.deleteItem(id, user.account.collective)
          res = BasicResult(n > 0, if (n > 0) "Item deleted" else "Item deletion failed.")
          resp <- Ok(res)
        } yield resp
    }
  }

  implicit final class OptionString(opt: Option[String]) {
    def notEmpty: Option[String] =
      opt.map(_.trim).filter(_.nonEmpty)
  }
}
