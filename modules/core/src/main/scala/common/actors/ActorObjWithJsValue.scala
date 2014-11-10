package common.actors

import akka.actor.{ActorRefFactory, Props}
import com.typesafe.config.Config
import play.api.libs.json.JsValue

trait ActorObjWithJsValue extends ActorObj {
  def props(config: JsValue) : Props
  def start(config: JsValue)(implicit f: ActorRefFactory) = f.actorOf(props(config), id)
}
