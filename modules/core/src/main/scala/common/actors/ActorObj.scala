package common.actors

import akka.actor.{Props, ActorRefFactory}
import com.typesafe.config.Config

trait ActorObj {
  def id: String
  def path(implicit f: ActorRefFactory) = f.actorSelection("/user/" + id)
}
