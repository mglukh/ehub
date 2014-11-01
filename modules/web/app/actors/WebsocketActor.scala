package actors

import akka.actor.{ActorRef, Props}
import hq.{Update, Command, Subject, Subscribe}
import hq.routing.MessageRouterActor
import nugget.core.actors.ActorWithComposableBehavior
import play.api.libs.json.{JsValue, Json}

object WebsocketActor {
  def props(out: ActorRef) = Props(new WebsocketActor(out))
}


class WebsocketActor(out: ActorRef)
  extends ActorWithComposableBehavior {


  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Accepted WebSocket connection, proxy actor: $out")
  }

  override def commonBehavior(): Receive = clientMessages orElse serverMessages orElse super.commonBehavior()

  def serverMessages: Receive = {
    case Update(Subject(subj, topic), data) =>
      val value: String = Json.obj(
        "topic" -> subj,
        "data" -> data
      ).toString()
      logger.info("!>>> " + value)
      logger.info("!>>> " + out)
      out ! value
  }

  def clientMessages: Receive = {
    case x: String =>
      logger.info(s"->Websocket: $x")

      val j = Json.parse(x)

      for (
        s <- (j \ "topic").asOpt[String];
        subj = Subject(s);
        t <- (j \ "type").asOpt[String];
        x <- t match {
          case "sub" => Some(Subscribe(subj))
          case "unsub" => Some(Subscribe(subj))
          case "cmd" => Some(Command(subj, (j \ "data").asOpt[JsValue]))
          case _ => None
        }
      ) MessageRouterActor.path ! x


  }

}
