package hq.gates

import akka.actor.{ActorRef, ActorRefFactory, Props}
import hq._
import hq.routing.MessageRouterActor
import nugget.core.actors.ActorWithComposableBehavior
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.collection.mutable


object GateManagerActor {
  def id = "gates"

  def props = Props(new GateManagerActor())

  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)
}

class GateManagerActor extends ActorWithComposableBehavior {

  val ListRequest = """/gates/list""".r
  val ListItemRequest = """/gates/list/(\w+)""".r
  val CmdAdd = """/gates/list/add""".r

  var gates: List[String] = List()

  override def commonBehavior(): Receive = handler orElse super.commonBehavior()

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent {
      case Subject(subj, _) if subj.startsWith("/gates/") => true
      case _ => false
    }
  }


  def handleSubscribe(subj: String) = subj match {
    case ListRequest() => Some(Json.toJson(gates.map { x => Json.obj("id" -> x) }.toArray))
    case ListItemRequest(id) =>
      context.child(id).foreach(_ ! Subscribe(Subject(subj)))
      None
  }

  def handleCommand(subj: String, maybeData: Option[JsValue]): JsValue = subj match {
    case CmdAdd() =>
      for (
        data <- maybeData;
        name <- (data \ "name").asOpt[String]
      ) {
        gates = gates :+ name
        GateActor.start(name)
      }
      Json.toJson(gates.map { x => Json.obj("id" -> x) }.toArray)
  }


  def handler: Receive = {

    case Subscribe(subj) =>
      handleSubscribe(subj.route) foreach { x => MessageRouterActor.path ! Image(Update(subj, x)) }
    case Command(subj, data) =>
      MessageRouterActor.path ! Image(Update(Subject("/gates/list"), handleCommand(subj.route, data)))


  }

}
