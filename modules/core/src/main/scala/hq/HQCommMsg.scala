package hq

import play.api.libs.json.JsValue

trait HQCommMsg {
  val subj: Subject
}

case class Subject(route: String, topic: Option[String] = None)

case class Subscribe(subj: Subject) extends HQCommMsg
case class Unsubscribe(subj: Subject) extends HQCommMsg
case class Command(subj: Subject, data: Option[JsValue] = None) extends HQCommMsg
case class Update(subj: Subject, data: JsValue) extends HQCommMsg



sealed trait UpdateScope
case class Image(update: Update, canBeCached: Boolean = true)
case class Fragment(update: Update)


case class RegisterComponent(mapper: Subject => Boolean)