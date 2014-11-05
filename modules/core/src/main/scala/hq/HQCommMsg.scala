package hq

import akka.actor.ActorRef
import play.api.libs.json.JsValue

trait HQCommMsg {
  val subj: Subject
}

case class Subject(route: String, topic: String)

case class Subscribe(subj: Subject) extends HQCommMsg
case class Unsubscribe(subj: Subject) extends HQCommMsg
case class Command(subj: Subject, data: Option[JsValue] = None) extends HQCommMsg
case class Update(subj: Subject, data: JsValue, canBeCached: Boolean = true) extends HQCommMsg
case class Discard(subj: Subject) extends HQCommMsg

case class RegisterComponent(route: String, ref: ActorRef)