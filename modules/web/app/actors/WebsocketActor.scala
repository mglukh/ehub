/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package actors

import akka.actor.{Actor, ActorRef, Props}
import common.actors.ActorWithComposableBehavior
import hq._
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

object WebsocketActor {
  def props(out: ActorRef) = Props(new WebsocketActor(out))
}


class WebsocketActor(out: ActorRef)
  extends ActorWithComposableBehavior {

  val alias2path: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  val path2alias: mutable.Map[String, String] = new mutable.HashMap[String, String]()

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Accepted WebSocket connection, proxy actor: $out")
  }

  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  private def messageHandler: Actor.Receive = {
    case Update(sourceRef, subj, data, _) =>
      path2alias get subj2path(subj) foreach (out ! buildClientMessage("U", _)(Json.stringify(data)))
    case Stale(sourceRef, subj) =>
      path2alias get subj2path(subj) foreach (out ! buildClientMessage("D", _)())

    case payload: String =>
      logger.debug(s"Received from Websocket: $payload")

      val mtype = payload.head
      val data = payload.tail

      mtype match {
        case 'A' => addOrReplaceAlias(data)
        case _ => extractByAlias(data) foreach { str =>
          extractSubjectAndPayload(str,
            processRequestByType(mtype, _, _) foreach(MessageRouterActor.path ! _)
          )}
      }
  }

  private def buildClientMessage(mt: String, alias: String)(payload: String = "") = mt + alias + "|" + payload


  private def subj2path(subj: Any) = subj match {
    case RemoteSubj(addr, LocalSubj(ComponentKey(compKey), TopicKey(topicKey))) => segments2path(addr, compKey, topicKey)
    case LocalSubj(ComponentKey(compKey), TopicKey(topicKey)) => segments2path("_", compKey, topicKey)
    case _ => "invalid"
  }

  private def segments2path(addr: String, component: String, topic: String) = addr + "|" + component + "|" + topic

  private def addOrReplaceAlias(value: String) = {

    val idx: Int = value.indexOf('|')

    val al = value.substring(0, idx)
    val path = value.substring(idx + 1)

    logger.info(s"Alias $al->$path")
    alias2path += al -> path
    path2alias += path -> al
  }

  private def processRequestByType(msgType: Char, subj: Subj, payload: Option[JsValue]) = msgType match {
    case 'S' => Some(Subscribe(self, subj))
    case 'U' => Some(Unsubscribe(self, subj))
    case 'C' => Some(Command(self, subj, payload))
    case _ =>
      logger.warn(s"Invalid message type: " + msgType)
      None
  }

  private def extractByAlias(value: String): Option[String] = {
    val idx: Int = value.indexOf('|')
    val al = value.substring(0, idx)
    val path = value.substring(idx)

    alias2path.get(al).map(_ + path)
  }

  private def extractSubjectAndPayload(str: String, f: (Subj, Option[JsValue]) => Unit) = {
    def extractPayload(list: List[String]) = list match {
      case Nil => None
      case x :: xs => Json.parse(x).asOpt[JsValue]
    }
    def extract(list: List[String]) = list match {
      case "_" :: comp :: topic :: tail => f(LocalSubj(ComponentKey(comp), TopicKey(topic)), extractPayload(tail))
      case addr :: comp :: topic :: tail => f(RemoteSubj(addr, LocalSubj(ComponentKey(comp), TopicKey(topic))), extractPayload(tail))
      case _ => logger.warn(s"Invalid payload $str")
    }
    extract(str.split('|').toList)
  }

}
