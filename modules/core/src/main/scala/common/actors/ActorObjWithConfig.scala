package common.actors

import akka.actor.{ActorRefFactory, Props}
import com.typesafe.config.Config

trait ActorObjWithConfig extends ActorObj {
  def props(implicit config: Config): Props

  def start(implicit f: ActorRefFactory, config: Config) = f.actorOf(props, id)
}
