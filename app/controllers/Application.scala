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

}
