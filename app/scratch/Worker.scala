package scratch

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

case class Name(first: String, last: String)
case class Worker(name: Name, age: Int)

object Workers {

  var list = List[Worker]()

  def add(worker: Worker) = {
    list = list ::: List(worker)
  }

  implicit val nameWrites: Writes[Name] = (
    (JsPath \ "first").write[String] and
    (JsPath \ "last").write[String]
  )(unlift(Name.unapply))

  implicit val workerWrites: Writes[Worker] = (
    (JsPath \ "name").write[Name] and
    (JsPath \ "age").write[Int]
  )(unlift(Worker.unapply))

}
