package common.actors

import akka.actor.{ActorRefFactory, Props}
import play.api.libs.json.JsValue

trait ActorObjWithoutConfig extends ActorObj {
  def props : Props
  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)
}
