package agent.shared

import akka.actor.ActorRef
import play.api.libs.json.JsValue

trait WireMessage

case class Acknowledgeable[T](msg: T, id: Long) extends WireMessage
case class Acknowledge(id: Long) extends WireMessage

case class Handshake(name: String) extends WireMessage
case class HandshakeResponse(ref: ActorRef) extends WireMessage

case class CreateFlow(config: JsValue) extends WireMessage
case class StartFlow(flowId: Long) extends WireMessage
case class StopFlow(flowId: Long) extends WireMessage


case class MessageWithAttachments[T](msg: T, attachments: JsValue) extends WireMessage
