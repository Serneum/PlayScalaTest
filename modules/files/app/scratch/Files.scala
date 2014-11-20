package scratch

import scala.collection.JavaConversions._

/**
 * Created by crr on 11/14/14.
 */
case class File (name: String, path: String)

object Files {
  var list = List[File]()

  def add(file: File) {
    list = list ::: List(file)
  }

  def remove(file: String) {
    list = list.filterNot(elm => elm.name == file)
  }
}
