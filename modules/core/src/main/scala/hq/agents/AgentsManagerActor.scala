package hq.agents

import agent.shared.Handshake
import akka.actor.{ActorRef, Actor, ActorRefFactory, Props}
import common.actors.{ActorWithComposableBehavior, ActorWithSubscribers}
import hq.{RegisterComponent, Subject}
import hq.routing.MessageRouterActor
import play.api.libs.json.Json

object AgentsManagerActor {
  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)

  def id = "agents"

  def props = Props(new AgentsManagerActor())
}

case class AgentAvailable(id: String)


class AgentsManagerActor extends ActorWithComposableBehavior with ActorWithSubscribers {
  override def commonBehavior(): Actor.Receive = handler orElse super.commonBehavior()

  val AGENTS_LIST = Subject("agents", "list")

  var agents: Map[String, ActorRef] = Map()

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent("agents", self)
  }


  def list = Some(Json.toJson(agents.keys.map { x => Json.obj("id" -> x)}.toArray))

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENTS_LIST => updateTo(subject, ref, list)
  }



  private def handler : Receive = {
    case Handshake(name) =>
      logger.info("Received handshake from " + sender())
      AgentProxyActor.start(name, sender())
    case AgentAvailable(name) =>
      agents = agents + (name -> sender())
      updateToAll(AGENTS_LIST, list)
  }

}
