package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scratch._

import java.io.File

object Application extends Controller {

  val index = Action { request =>
    Ok(views.html.index("You are not prepared!"))
  }

  val workers = Action { request =>
    Workers.add(new Worker(new Name("c", "r"), 24))
    Workers.add(new Worker(new Name("n", "c"), 23))
    Ok(views.html.workers(Workers.list))
  }

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
      Redirect(routes.Application.fileList)
    }
    .getOrElse {
      Redirect(routes.Application.fileList).flashing("error" -> "Missing file")
    }
  }

  def deleteFile(name: String) = Action(parse.anyContent) { request =>
    val filePath = "files/" + name
    val file = new java.io.File(filePath)
    if (file.exists()) {
      if (file.delete()) {
        Files.remove(name)
        Redirect(routes.Application.fileList)
      }
      Redirect(routes.Application.fileList).flashing("error" -> s"Could not delete '$name'")
    }
    Redirect(routes.Application.fileList).flashing("error" -> s"'$name' does not exist.")
  }

}
