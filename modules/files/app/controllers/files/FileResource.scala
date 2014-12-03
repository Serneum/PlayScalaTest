package controllers.files

import java.util.UUID

import play.api._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws._
import settings.SystemSettings
import scala.concurrent.{Await, Future}
import play.api.mvc._
import scratch._
import java.io.File
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object FileResource extends Controller {

  val list = Action.async { implicit request =>
    val futureFilesList = getAllFiles()
    futureFilesList.map(filesList => Ok(views.html.fileList(filesList)))
  }

  val upload = Action(parse.multipartFormData) { request =>
    request.body.file("file").map { file =>
      val fileName = file.filename
      val id = UUID.randomUUID
      val filePath = "files/" + id
      val uploadedFile = new File(filePath)
      uploadedFile.getParentFile().mkdirs()
      file.ref.moveTo(uploadedFile)
      insertIntoTable(id, fileName, filePath)
      Redirect(controllers.files.routes.FileResource.list)
    }
    .getOrElse {
      Redirect(controllers.files.routes.FileResource.list).flashing("error" -> "Missing file")
    }
  }

  def deleteFile(id: UUID, rev: String) = Action.async(parse.anyContent) { request =>
    val futureRemovedFromDb = removeFromTable(id, rev)
    futureRemovedFromDb.map(removedFromDb =>
      if (removedFromDb) {
        val filePath = "files/" + id
        val file = new File(filePath)
        if (file.exists()) {
          if (file.delete()) {
            Redirect(controllers.files.routes.FileResource.list)
          }
          // Explicitly put the else statement because otherwise the statement will always run. Looks like Redirect() isn't like a return
          else {
            Redirect(controllers.files.routes.FileResource.list).flashing("error" -> s"Could not delete '$filePath'")
          }
        }
        else {
          Redirect(controllers.files.routes.FileResource.list).flashing("error" -> s"'$filePath' does not exist.")
        }
      }
      else {
        Redirect(controllers.files.routes.FileResource.list).flashing("error" -> s"id: '$id', rev: '$rev' could not be removed from the database.")
      }
    )
  }

  //******************************************
  // REST calls to CouchDB
  //******************************************
  def getAllFiles(): Future[List[FileRep]] = {
    var filesResult = List[FileRep]()
    val filesDbUrl = SystemSettings.DB_URL + SystemSettings.FILES_DB + "_all_docs"
    val holder: WSRequestHolder = WS.url(filesDbUrl).withQueryString("include_docs" -> "true")
    return holder.get().map { response =>
      // Do the work here so we can directly return a Future and not worry about how to guarantee that all data is present when rendering the view
      for (jsVal <- (response.json \ "rows").as[List[JsValue]]) {
        val doc: JsValue = (jsVal \ "doc")
        filesResult = filesResult ::: List(doc.as[FileRep])
      }
      filesResult
    }
  }

  def insertIntoTable(id: UUID, name: String, path: String) {
    val filesDbUrl = SystemSettings.DB_URL + SystemSettings.FILES_DB + id
    val holder: WSRequestHolder = WS.url(filesDbUrl)
    val json = Json.obj(
      "_id"   -> id,
      "name" -> name,
      "path" -> path
    )
    holder.put(json)
  }

  def removeFromTable(id: UUID, rev: String): Future[Boolean] = {
    val filesDbUrl = SystemSettings.DB_URL + SystemSettings.FILES_DB + id
    val holder: WSRequestHolder = WS.url(filesDbUrl).withQueryString("rev" -> rev)
    holder.delete().map { response =>
      (response.json \ "ok").as[JsBoolean].value
    }
  }

  // Can't seem to put this in the FileRep class file. Not sure why, but this isn't a terrible place either
  implicit val fileReads: Reads[FileRep] = (
    (JsPath \ "_id").read[UUID] and
    (JsPath \ "name").read[String] and
    (JsPath \ "path").read[String] and
    (JsPath \ "_rev").read[String]
  )(FileRep.apply _)

}
