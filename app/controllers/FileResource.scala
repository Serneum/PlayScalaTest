package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scratch._

import java.io.File

object FileResource extends Controller {

  val fileList = Action { implicit request =>
    Ok(views.html.fileList(Files.list))
  }

  val upload = Action(parse.multipartFormData) { request =>
    request.body.file("file").map { file =>
      val fileName = file.filename
      val filePath = "files/" + fileName
      val uploadedFile = new java.io.File(filePath);
      uploadedFile.getParentFile().mkdirs()
      file.ref.moveTo(uploadedFile)
      Files.add(new scratch.File(fileName, filePath))
      Redirect(routes.FileResource.fileList)
    }
    .getOrElse {
      Redirect(routes.FileResource.fileList).flashing("error" -> "Missing file")
    }
  }

  def deleteFile(name: String) = Action(parse.anyContent) { request =>
    val filePath = "files/" + name
    val file = new java.io.File(filePath)
    if (file.exists()) {
      if (file.delete()) {
        Files.remove(name)
        Redirect(routes.FileResource.fileList)
      }
      Redirect(routes.FileResource.fileList).flashing("error" -> s"Could not delete '$name'")
    }
    Redirect(routes.FileResource.fileList).flashing("error" -> s"'$name' does not exist.")
  }

}
