package common.actors

import akka.actor.Actor

trait ActorUtils extends Actor {

  def actorFriendlyId(id: String) = id.replaceAll("""[\W]""", "_").replaceAll("__", "_")

  def hasChildWithId(id: String) = context.child(actorFriendlyId(id)).isDefined

}
