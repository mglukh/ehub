package actors

import akka.actor.{Actor, ActorRef, Props}
import common.actors.ActorWithComposableBehavior
import hq._
import hq.routing.MessageRouterActor
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

  override def commonBehavior(): Actor.Receive = clientMessages orElse serverMessages orElse super.commonBehavior()

  def serverMessages: Actor.Receive = {
    case Update(Subject(subj, topic), data, _) =>
      val value: String = "U|" + subj + "|" + topic + "|" + data.toString()
      logger.info("!>>> " + value)
      logger.info("!>>> " + out)
      out ! value
    case Discard(Subject(subj, topic)) =>
      val value: String = "D|" + subj + "|" + topic + "|"
      logger.info("!>>> " + value)
      out ! value
  }


  def clientMessages: Actor.Receive = {
    case x: String =>
      logger.info(s"->Websocket: $x")

      x.split('|') match {
        case Array(msgType, route, topic) =>
          val subj = Subject(route, topic)
          val msg = msgType match {
            case "S" => Some(Subscribe(subj))
            case "U" => Some(Unsubscribe(subj))
            case _ =>
              logger.warn(s"Invalid message type: " + msgType)
              None
          }
          msg foreach(MessageRouterActor.path ! _)
        case Array(msgType, route, topic, payload) =>
          val subj = Subject(route, topic)
          val msg = msgType match {
            case "C" => Some(Command(subj, Json.parse(payload).asOpt[JsValue]))
            case _ =>
              logger.warn("Invalid message type: " + msgType)
              None
          }
          msg foreach(MessageRouterActor.path ! _)
        case x => logger.warn("Invalid message format" + x)
      }

  }

}
