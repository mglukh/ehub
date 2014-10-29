package actors

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, Props}
import nugget.core.actors.ActorWithComposableBehavior
import play.api.libs.json.Json

object WebsocketActor {
  def props(out: ActorRef) = Props(new WebsocketActor(out))
}


class WebsocketActor(out: ActorRef)
  extends ActorWithComposableBehavior {


  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Accepted WebSocket connection, proxy actor: $out")
  }

  override def commonBehavior(): Receive = clientMessages orElse super.commonBehavior()

  def clientMessages : Receive = {
    case x:String =>
      logger.info(s"->Websocket: $x")

      val j = Json.parse(x)

      (j \ "req").asOpt[String] foreach{ _ => out ! Json.arr(
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
      ).toString() }


  }

}
