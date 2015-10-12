package actors

import akka.actor.{ Props, Actor, ActorRef }
import dto.HashtagAcrossStream
import play.api.Logger
import play.api.libs.json.{JsValue, Json, Writes}

object TwitterAnalyticsActor {
  implicit val pairWritesWrites = new Writes[(String, Long)] {
    def writes(pair: (String, Long)): JsValue = Json.obj(
      "key" -> pair._1,
      "count" -> pair._2
    )
  }

  implicit val hashtagAcrossStreamWrites = new Writes[HashtagAcrossStream] {
    def writes(h: HashtagAcrossStream) = Json.obj(
      "pairs" ->
        h.pairs.map(pair => Json.toJson(pair))
    )
  }

  def props(jsClient: ActorRef) = Props(new TwitterAnalyticsActor(jsClient))
}

class TwitterAnalyticsActor(jsClient: ActorRef) extends Actor {
  import TwitterAnalyticsActor._

  def receive = {
    //    case msg: String =>
    //      jsClient ! msg
    case hashtags:HashtagAcrossStream =>
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

