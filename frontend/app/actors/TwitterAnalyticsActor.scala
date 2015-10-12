package actors

import akka.actor.{Props, Actor, ActorRef}
import dto.MostPopular
import play.api.Logger
import play.api.libs.json.{JsString, JsValue, Json, Writes}

object TwitterAnalyticsActor {
  implicit val pairWritesWrites = new Writes[(String, Long)] {
    def writes(pair: (String, Long)): JsValue = Json.obj(
      "key" -> pair._1,
      "count" -> pair._2
    )
  }

  implicit val hashtagAcrossStreamWrites = new Writes[MostPopular] {
    def writes(h: MostPopular) = Json.obj(
      "type" -> JsString(h.dataType),
      "pairs" -> h.pairs.map(pair => Json.toJson(pair))
    )
  }

  def props(jsClient: ActorRef) = Props(new TwitterAnalyticsActor(jsClient))
}

class TwitterAnalyticsActor(jsClient: ActorRef) extends Actor {

  import TwitterAnalyticsActor._

  def receive = {
    case hashtags: MostPopular =>
      val jsString = Json.toJson(hashtags).toString()
      Logger.info(
        s"""
           |received
           |$hashtags
           |json format
           |$jsString
         """.stripMargin)
      jsClient ! jsString
  }
}

