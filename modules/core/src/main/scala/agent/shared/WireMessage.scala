package agent.shared

import akka.actor.ActorRef
import play.api.libs.json.JsValue

trait WireMessage

trait AgentControllerMessage extends WireMessage
trait AgentMessage extends WireMessage

case class Acknowledgeable[T](msg: T, id: Long) extends AgentMessage
case class Acknowledge(id: Long) extends AgentMessage
case class MessageWithAttachments[T](msg: T, attachments: JsValue) extends AgentMessage


case class Handshake(ref: ActorRef, name: String) extends AgentControllerMessage
case class CommunicationProxyRef(ref: ActorRef) extends AgentControllerMessage

case class GenericJSONMessage(json: String)


case class CreateTap(config: JsValue) extends AgentControllerMessage
case class OpenTap() extends AgentControllerMessage
case class CloseTap() extends AgentControllerMessage
case class RemoveTap() extends AgentControllerMessage


