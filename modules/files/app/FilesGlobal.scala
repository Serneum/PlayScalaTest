/**
 * Created by crr on 11/20/14.
 */
import play.api._
import play.api.Play.current
import play.api.libs.ws._
import settings.SystemSettings
import scala.concurrent.Future
import scala.util.Success

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    createFilesTableIfNeeded()
  }

  def createFilesTableIfNeeded() {
    implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext
    val filesDbUrl = SystemSettings.DB_URL + SystemSettings.FILES_DB
    val holder: WSRequestHolder = WS.url(filesDbUrl)
    val result: Future[String] = holder.get().map { response =>
      (response.json \ "error").as[String]
    }

    result.onSuccess {
      case "not_found" =>
        Logger.info("Creating the Files table because it was not found in the database..")
        holder.put(filesDbUrl)
    }
  }
}
