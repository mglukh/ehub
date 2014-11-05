package hq.agents

import agent.shared.HandshakeResponse
import akka.actor.{ActorRef, Actor, Props, ActorRefFactory}
import common.actors.{ActorWithSubscribers, PipelineWithStatesActor}
import hq.{RegisterComponent, Subject}
import hq.gates.{GateAvailable, GateActor}
import hq.routing.MessageRouterActor
import play.api.libs.json.Json

/**
 * Created by maks on 5/11/2014.
 */
object AgentProxyActor {
  def props(id: String, ref: ActorRef) = Props(new AgentProxyActor(id,ref))

  def start(id: String, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(id,ref), id)
}

class AgentProxyActor(id: String, ref: ActorRef) extends PipelineWithStatesActor with ActorWithSubscribers {

  def route = "agent/" + id

  val AGENT_X_INFO = Subject(route, "info")



  override def preStart(): Unit = {
    super.preStart()
    ref ! HandshakeResponse(self)
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! AgentAvailable(route)
  }

  override def commonBehavior(): Actor.Receive = super.commonBehavior()

  def updateToAll():Unit = updateToAll(AGENT_X_INFO, info)

  def info = Some(Json.obj(
    "name" -> id,
    "text" -> s"some random text from $id",
    "state" -> "active"
  ))

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENT_X_INFO => updateTo(subject, ref, info)
  }



}