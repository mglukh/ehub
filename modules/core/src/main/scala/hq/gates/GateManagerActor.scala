package hq.gates

import akka.actor.{ActorRefFactory, Props}
import hq._
import hq.routing.MessageRouterActor
import nugget.core.actors.ActorWithComposableBehavior
import play.api.libs.json.{JsValue, Json}


object GateManagerActor {
  def id = "gates"

  def props = Props(new GateManagerActor())

  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)
}

class GateManagerActor extends ActorWithComposableBehavior {

  val ListRequest = """/gates/list""".r
  val ListItemRequest = """/gates/list/(\d+)""".r
  val CmdAdd = """/gates/list/add""".r

  override def commonBehavior(): Receive = handler orElse super.commonBehavior()

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent {
      case Subject(subj, _) if subj.startsWith("/gates/list") => true
      case _ => false
    }
  }


  def handleSubscribe(subj: String) = subj match {
    case ListRequest() => Json.arr(
      Json.obj("id" -> 1),
      Json.obj("id" -> 2),
      Json.obj("id" -> 3),
      Json.obj("id" -> 4)
    )
    case ListItemRequest(id) => Json.obj(
      "text" -> s"sample text $id",
      "username" -> s"user$id",
      "avatar" -> "/assets/images/avatar-02.svg",
      "favorite" -> false
    )
  }

  def handleCommand(subj: String, maybeData: Option[JsValue]): JsValue = subj match {
    case CmdAdd() => Json.arr(
      Json.obj("id" -> 1),
      Json.obj("id" -> 2),
      Json.obj("id" -> 3),
      Json.obj("id" -> 4),
      Json.obj("id" -> 5)
    )
  }


  def handler: Receive = {

    case Subscribe(subj) =>
      MessageRouterActor.path ! Image(Update(subj, handleSubscribe(subj.route)))
    case Command(subj, data) =>
      MessageRouterActor.path ! Image(Update(Subject("/gates/list"), handleCommand(subj.route, data)))


  }

}
