package actors

import akka.actor.ActorSystem
import play.api.Play
import play.api.Play.current

object ActorCommons {
  lazy val system = ActorSystem(Play.configuration.getString("actor.systemName").get)
}
