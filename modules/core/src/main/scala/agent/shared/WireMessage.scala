package agent.shared

import akka.actor.ActorRef
import play.api.libs.json.JsValue

trait WireMessage

trait AgentControllerMessage extends WireMessage
trait AgentMessage extends WireMessage

case class Acknowledgeable[T](msg: T, id: Long) extends AgentMessage
case class Acknowledge(id: Long) extends AgentMessage
case class MessageWithAttachments[T](msg: T, attachments: JsValue) extends AgentMessage


case class Handshake(name: String) extends AgentControllerMessage
case class CommunicationProxyRef(ref: ActorRef) extends AgentControllerMessage

case class GenericJSONMessage(json: String)


case class CreateFlow(config: JsValue) extends AgentControllerMessage
case class StartFlow(flowId: Long) extends AgentControllerMessage
case class StopFlow(flowId: Long) extends AgentControllerMessage


