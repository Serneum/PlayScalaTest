package controllers.files

import java.util.UUID

import play.api._
import play.api.Play.current
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws._
import play.api.mvc.MultipartFormData.FilePart
import settings.SystemSettings
import scala.concurrent.{Await, Future}
import play.api.mvc._
import scratch._
import java.io.{FileNotFoundException, File}
import scala.util.{Success, Failure}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object FileResource extends Controller {

  def list = Action.async { implicit request =>
    for {
      filesList <- getAllFiles()
    }
    yield {
      Ok(views.html.fileList(filesList))
    }
  }

  def upload = Action(parse.multipartFormData) { request =>
    request.body.file("file").map { file =>
      for {
        filesList <- getAllFiles()
      }
      yield {
        val fileName = file.filename
        var id: UUID = null
        var filePath: String = null
        var found = false
        var rev: String = null
        for (fileRep <- filesList) {
          if (fileRep.name == fileName) {
            id = fileRep._id
            filePath = fileRep.path
            rev = fileRep._rev
            found = true
          }
        }

        // If the file already exists, delete it and replace it with the new version
        if (found) {
          val existingFile = new File(filePath)
          if (existingFile.exists()) {
            existingFile.delete()
          }
          updateFile(id, fileName, filePath, rev)
        }
        else {
          id = UUID.randomUUID
          filePath = "files/" + id
          insertIntoTable(id, fileName, filePath)
        }

        val uploadedFile = new File(filePath)
        uploadedFile.getParentFile().mkdirs()
        file.ref.moveTo(uploadedFile)
      }
      Redirect(controllers.files.routes.FileResource.list)
    }
    .getOrElse {
      Redirect(controllers.files.routes.FileResource.list).flashing("error" -> "Missing file")
    }
  }

  def delete(id: String) = Action.async(parse.anyContent) { request =>
    val uuid = UUID.fromString(id)
    for {
      doc <- getDocumentById(uuid)
      rev = doc._rev
      removedFromDb <- removeFromTable(uuid, rev)
    }
    yield {
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
    }
  }

  def download(id: UUID) = Action.async {
    for {
      doc <- getDocumentById(id)
    }
    yield {
      val file = new File(doc.path)
      if (file.exists()) {
        Ok.sendFile(
          content = file,
          fileName = _ => doc.name
        )
      }
      else {
        NotFound
      }
    }
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

  def getDocumentById(id: UUID): Future[FileRep] = {
    val filesDbUrl = SystemSettings.DB_URL + SystemSettings.FILES_DB + id
    val holder: WSRequestHolder = WS.url(filesDbUrl)
    holder.get().map { response =>
      (response.json).as[FileRep]
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

  def updateFile(id: UUID, name: String, path: String, rev: String) {
    val filesDbUrl = SystemSettings.DB_URL + SystemSettings.FILES_DB + id
    val holder: WSRequestHolder = WS.url(filesDbUrl)
    val json = Json.obj(
      "_id"   -> id,
      "name" -> name,
      "path" -> path,
      "_rev" -> rev
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

  //******************************************
  // Javascript Routes
  //******************************************
  def javascriptRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        controllers.files.routes.javascript.FileResource.delete
      )
    ).as("text/javascript")
  }

  // Can't seem to put this in the FileRep class file. Not sure why, but this isn't a terrible place either
  implicit val fileReads: Reads[FileRep] = (
    (JsPath \ "_id").read[UUID] and
    (JsPath \ "name").read[String] and
    (JsPath \ "path").read[String] and
    (JsPath \ "_rev").read[String]
  )(FileRep.apply _)

}
