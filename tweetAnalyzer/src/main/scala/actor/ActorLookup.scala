package actor

import akka.actor.{ActorSystem, ActorSelection}

object ActorLookup {
  lazy val system = ActorSystem("tweets-analyzer-system")
  var actor: Option[ActorSelection] = None

  def lookup(selectionPath: String): ActorSelection =
    actor.fold {
      val selection = system.actorSelection(selectionPath)
      actor = Some(selection)
      selection
    } {
      a => a
    }
}
