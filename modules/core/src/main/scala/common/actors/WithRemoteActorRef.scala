package common.actors

import akka.actor.ActorRef

trait WithRemoteActorRef {

  def remoteActorRef : Option[ActorRef]

}
