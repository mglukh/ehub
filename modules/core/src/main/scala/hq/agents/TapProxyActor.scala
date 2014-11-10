package hq.agents

import agent.shared._
import akka.actor._
import akka.remote.DisassociatedEvent
import common.actors.{ActorWithSubscribers, PipelineWithStatesActor}
import hq.{RegisterComponent, Subject}
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsArray, JsValue, Json}


object TapProxyActor {
  def start(parentRoute: String, id: String, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(parentRoute, id, ref), id)

  def props(parentRoute: String, id: String, ref: ActorRef) = Props(new TapProxyActor(parentRoute, id, ref))
}

class TapProxyActor(parentRoute: String, id: String, ref: ActorRef) extends PipelineWithStatesActor with ActorWithSubscribers {

  val route = parentRoute + "/" + id
  val AGENT_X_TAP_X_INFO = Subject(route, "info")

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
    (json \ "info").asOpt[JsValue] foreach processInfo
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received tap info update: $info")
    updateToAll(AGENT_X_TAP_X_INFO, info)
  }

  override def preStart(): Unit = {
    super.preStart()
    ref ! CommunicationProxyRef(self)
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! TapAvailable(route)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
  }


  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENT_X_TAP_X_INFO => updateTo(subject, ref, info)
  }

  override def processCommand(ref: ActorRef, subject: Subject, maybeData: Option[JsValue]) = subject.topic match {
    case "start" => ref ! OpenTap()
    case "stop" => ref ! CloseTap()
    case "kill" => ref ! RemoveTap()
  }


}
