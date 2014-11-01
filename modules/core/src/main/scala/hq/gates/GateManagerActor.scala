package hq.gates

import akka.actor.{ActorRefFactory, ActorContext, Props}
import hq._
import hq.routing.MessageRouterActor
import nugget.core.actors.ActorWithComposableBehavior
import play.api.libs.json.Json


object GateManagerActor {
  def id = "gates"
  def props = Props(new GateManagerActor())
  def start(implicit f:ActorRefFactory) = f.actorOf(props, id)
}
class GateManagerActor extends ActorWithComposableBehavior {

  def routeId = "/" + GateManagerActor.id

  override def commonBehavior(): Receive = handler orElse super.commonBehavior()

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent {
      case Subject("/gates/list",_) => true
    }
  }

  def handler : Receive = {

    case Subscribe(_) =>
      MessageRouterActor.path ! Image(Update(Subject("/gates/list"), Json.arr(
        Json.obj(
          "uid" -> 1,
          "text" -> "sample text 1",
          "username" -> "user1",
          "avatar" -> "/assets/images/avatar-02.svg",
          "favorite" -> false
        ),
        Json.obj(
          "uid" -> 2,
          "text" -> "sample text 2",
          "username" -> "user2",
          "avatar" -> "/assets/images/avatar-03.svg",
          "favorite" -> false
        ),
        Json.obj(
          "uid" -> 3,
          "text" -> "sample text 3",
          "username" -> "user3",
          "avatar" -> "/assets/images/avatar-04.svg",
          "favorite" -> false
        )
      )))


  }

}
