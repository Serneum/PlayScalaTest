package controllers.files

import play.api.mvc._
import scratch._

object FileResource extends Controller {

  val list = Action { implicit request =>
    Ok(views.html.fileList(Files.list))
  }

  val upload = Action(parse.multipartFormData) { request =>
    request.body.file("file").map { file =>
      val fileName = file.filename
      val filePath = "files/" + fileName
      val uploadedFile = new java.io.File(filePath)
      uploadedFile.getParentFile().mkdirs()
      file.ref.moveTo(uploadedFile)
      Files.add(new scratch.File(fileName, filePath))
      Redirect(controllers.files.routes.FileResource.list)
    }
    .getOrElse {
      Redirect(controllers.files.routes.FileResource.list).flashing("error" -> "Missing file")
    }
  }

  def deleteFile(name: String) = Action(parse.anyContent) { request =>
    val filePath = "files/" + name
    val file = new java.io.File(filePath)
    if (file.exists()) {
      if (file.delete()) {
        Files.remove(name)
        Redirect(controllers.files.routes.FileResource.list)
      }
      Redirect(controllers.files.routes.FileResource.list).flashing("error" -> s"Could not delete '$name'")
    }
    Redirect(controllers.files.routes.FileResource.list).flashing("error" -> s"'$name' does not exist.")
  }

}
