package hq.gates

import akka.actor._
import common.actors.{ActorWithComposableBehavior, ActorWithSubscribers}
import hq._
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsValue, Json}


object GateManagerActor {
  def id = "gates"

  def props = Props(new GateManagerActor())

  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)
}

case class GateAvailable(id: String)

class GateManagerActor extends ActorWithComposableBehavior
with ActorWithSubscribers {

  val GATES_LIST = Subject("gates", "list")

  var gates: Map[String, ActorRef] = Map()

  override def commonBehavior(): Actor.Receive = handler orElse super.commonBehavior()

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent("gates", self)
  }


  def list = Some(Json.toJson(gates.keys.map { x => Json.obj("id" -> x)}.toArray))

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case GATES_LIST => updateTo(subject, ref, list)
  }


  def handleCommand(topic: String, maybeData: Option[JsValue]) = topic match {
    case "add" =>
      for (
        data <- maybeData;
        name <- (data \ "name").asOpt[String]
      ) {
        val actor = GateActor.start(name)
        context.watch(actor)
      }
  }


  def handler: Receive = {
    case Command(subj, data) =>
      handleCommand(subj.topic, data)
    case GateAvailable(route) =>
      gates = gates + (route -> sender())
      updateToAll(GATES_LIST, list)
    case Terminated(ref) =>
      gates = gates.filter {
        case (route,otherRef) => otherRef != ref
      }
      updateToAll(GATES_LIST, list)
  }


}
