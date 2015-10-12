package config

import play.api.Play
import play.api.Play.current
import scalaz.{ \/-, -\/, \/ }

//object ConfigHelper {
//  val config = Play.configuration
//
//  def getString(key: String): Exception \/ String =
//    config.getString(key).fold[Exception \/ String] {
//      -\/(new Exception(s"$key not defined in application.conf"))
//    } {
//      v => if (v == "") -\/(new Exception(s"$key value is empty in application.conf")) else \/-(v)
//    }
//
//  def getInt(key: String): Exception \/ Int =
//    config.getInt(key).fold[Exception \/ Int] {
//      -\/(new Exception(s"$key not defined in application.conf"))
//    } {
//      v => \/-(v)
//    }
//
//}
