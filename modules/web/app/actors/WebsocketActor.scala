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

import javax.security.auth.Subject

import akka.actor.{Actor, ActorRef, Props}
import common.actors.{ActorWithClusterAwareness, ActorWithComposableBehavior}
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

  override def commonBehavior(): Actor.Receive = clientMessages orElse serverMessages orElse super.commonBehavior()

  def serverMessages: Actor.Receive = {
    case Update(RemoteComponentKey(address, ComponentKey(route, TopicKey(topic))), data, _) =>
      path2alias.get(address + "|" + route + "|" + topic) foreach { alias =>
        val value: String = "U" + alias + "|" + data.toString()
        out ! value
      }

    case Stale(RemoteComponentKey(address, ComponentKey(route, TopicKey(topic)))) =>
      path2alias.get(address + "|" + route + "|" + topic) foreach { alias =>
        val value: String = "D" + alias + "|"
        out ! value
      }
  }

  def clientMessages: Actor.Receive = {
    case x: String =>
      logger.info(s"->Websocket: $x")

      x.head match {
        case 'A' =>
          alias(x.tail)
        case mt =>
          extractByAlias(x.tail) foreach (processClientRequest(mt, _))
      }

  }

  def alias(value: String) = {

    val idx: Int = value.indexOf('|')

    val al = value.substring(0, idx)
    val path = value.substring(idx + 1)

    logger.info(s"Alias $al->$path")
    alias2path += al -> path
    path2alias += path -> al
  }

  def processClientRequest(msgType: Char, x: String) = {
    x.split('|') match {
      case Array(address, route, topic) =>
        val subj = RemoteComponentKey(address, ComponentKey(route, TopicKey(topic)))
        val msg = msgType match {
          case 'S' => Some(Subscribe(subj))
          case 'U' => Some(Unsubscribe(subj))
          case _ =>
            logger.warn(s"Invalid message type: " + msgType)
            None
        }
        msg foreach (MessageRouterActor.path ! _)
      case Array(address, route, topic, payload) =>
        val subj = RemoteComponentKey(address, ComponentKey(route, TopicKey(topic)))
        val msg = msgType match {
          case 'C' => Some(Command(subj, Json.parse(payload).asOpt[JsValue]))
          case _ =>
            logger.warn("Invalid message type: " + msgType)
            None
        }
        msg foreach (MessageRouterActor.path ! _)
      case m => logger.warn("Invalid message format" + m)
    }

  }

  def extractByAlias(value: String): Option[String] = {
    val idx: Int = value.indexOf('|')
    val al = value.substring(0, idx)
    val path = value.substring(idx)

    alias2path.get(al).map(_ + path)
  }

}
