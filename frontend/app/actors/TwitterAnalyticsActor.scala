package actors

import akka.actor.{Props, Actor, ActorRef}
import dto.MostPopular
import play.api.Logger
import play.api.libs.json.{JsString, JsValue, Json, Writes}

object TwitterAnalyticsActor {
  /** 
   * serialize scala tuple to json format using play json api
   */
  implicit val pairWritesWrites = new Writes[(String, Long)] {
    def writes(pair: (String, Long)): JsValue = Json.obj(
      "key" -> pair._1,
      "count" -> pair._2
    )
  }

  /**
   * serialize scala data object to json format using play json api
   */
  implicit val hashtagAcrossStreamWrites = new Writes[MostPopular] {
    def writes(h: MostPopular) = Json.obj(
      "type" -> JsString(h.dataType),
      "pairs" -> h.pairs.map(pair => Json.toJson(pair))
    )
  }

  def props(jsClient: ActorRef) = Props(new TwitterAnalyticsActor(jsClient))
}

/**
 * receive analyzed data from TweetsAnalyzer, send the data to jsClient
 * which is the web socket client
 */
class TwitterAnalyticsActor(jsClient: ActorRef) extends Actor {

  import TwitterAnalyticsActor._

  def receive = {
    case hashtags: MostPopular =>
      val jsString = Json.toJson(hashtags).toString()
      Logger.debug(
        s"""
           |received
           |$hashtags
           |sending out json format
           |$jsString
         """.stripMargin)
      jsClient ! jsString
  }
}

