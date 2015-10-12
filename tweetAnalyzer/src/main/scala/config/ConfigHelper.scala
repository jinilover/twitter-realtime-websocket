package config

import com.typesafe.config.ConfigFactory

import scalaz.{-\/, \/, \/-}

//object ConfigHelper {
//  val config = ConfigFactory.load()
//
//  def getString(key: String): Exception \/ String =
//    try {
//      val v = config.getString(key)
//      if (v == "") -\/(new Exception(s"$key value is empty in application.conf")) else \/-(v)
//    } catch {
//      case ex: Exception => -\/(ex)
//    }
//
//  def getInt(key: String): Exception \/ Int =
//    try {
//      \/-(config.getInt(key))
//    } catch {
//      case ex: Exception => -\/(ex)
//    }
//
//}
