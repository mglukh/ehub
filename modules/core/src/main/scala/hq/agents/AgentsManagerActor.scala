package hq.agents

import agent.shared.Handshake
import akka.actor._
import akka.remote.DisassociatedEvent
import common.actors.{ActorWithComposableBehavior, ActorWithSubscribers}
import hq.routing.MessageRouterActor
import hq.{RegisterComponent, Subject}
import play.api.libs.json.{JsValue, Json}

object AgentsManagerActor {
  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)

  def id = "agents"

  def props = Props(new AgentsManagerActor())
}

case class AgentAvailable(id: String)


class AgentsManagerActor extends ActorWithComposableBehavior with ActorWithSubscribers {
  val AGENTS_LIST = Subject("agents", "list")
  var agents: Map[String, ActorRef] = Map()

  override def commonBehavior(): Actor.Receive = handler orElse super.commonBehavior()

  private def handler: Receive = {
    case Handshake(ref, name) =>
      logger.info("Received handshake from " + ref)
      context.watch(AgentProxyActor.start(name, ref))
    case AgentAvailable(name) =>
      agents = agents + (name -> sender())
      updateToAll(AGENTS_LIST, list)
    case Terminated(ref) =>
      agents = agents.filter {
        case (name, otherRef) => otherRef != ref
      }
      updateToAll(AGENTS_LIST, list)
  }

  def list = Some(Json.toJson(agents.keys.map { x => Json.obj("id" -> x)}.toArray))

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent("agents", self)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENTS_LIST => updateTo(subject, ref, list)
  }

}
