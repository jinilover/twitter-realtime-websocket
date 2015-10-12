package actors

import akka.actor.{ Props, Actor, ActorRef }
import dto.HashtagAcrossStream
import play.api.Logger

class TwitterAnalyticsActor(jsClient: ActorRef) extends Actor {
  def receive = {
    case msg: String =>
      jsClient ! msg
    case hashtags:HashtagAcrossStream =>
      Logger.debug(s"received $hashtags")
      jsClient ! hashtags.toString
  }
}

object TwitterAnalyticsActor {
  def props(jsClient: ActorRef) = Props(new TwitterAnalyticsActor(jsClient))
}