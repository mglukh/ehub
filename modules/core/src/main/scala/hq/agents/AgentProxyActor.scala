package hq.agents

import agent.shared.{CommunicationProxyRef, GenericJSONMessage}
import akka.actor._
import akka.remote.DisassociatedEvent
import common.actors.{ActorWithSubscribers, PipelineWithStatesActor}
import hq.routing.MessageRouterActor
import hq.{RegisterComponent, Subject}
import play.api.libs.json.{JsValue, Json}

/**
 * Created by maks on 5/11/2014.
 */
object AgentProxyActor {
  def start(id: String, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(id, ref), id)

  def props(id: String, ref: ActorRef) = Props(new AgentProxyActor(id, ref))
}

class AgentProxyActor(id: String, ref: ActorRef) extends PipelineWithStatesActor with ActorWithSubscribers {
  val route = "agent/" + id
  val AGENT_X_INFO = Subject(route, "info")
  private var info: Option[JsValue] = None

  override def commonBehavior(): Actor.Receive = commonMessageHandler orElse super.commonBehavior()

  private def commonMessageHandler: Receive = {
    case GenericJSONMessage(jsonString) =>
      Json.parse(jsonString).asOpt[JsValue] foreach process
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        self ! PoisonPill
      }
  }

  private def process(json: JsValue) = {
    (json \ "info").asOpt[JsValue] foreach { infoVal =>
      logger.info("!>>> Ok received " + infoVal)
      info = Some(infoVal)
      logger.info(s"!>>> $AGENT_X_INFO -> $info")

      updateToAll(AGENT_X_INFO, info)
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    ref ! CommunicationProxyRef(self)
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! AgentAvailable(route)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
  }


  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENT_X_INFO => updateTo(subject, ref, info)
  }


}