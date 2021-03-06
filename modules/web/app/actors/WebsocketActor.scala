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

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import com.diogoduailibe.lzstring4j.LZString
import common.actors.{ActorWithComposableBehavior, ActorWithTicks}
import hq._
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable
import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object WebsocketActor {
  def props(out: ActorRef) = Props(new WebsocketActor(out))
}


class WebsocketActor(out: ActorRef)
  extends ActorWithComposableBehavior
  with ActorWithTicks {

  val opSplitChar: Char = 1.toChar
  val msgSplitChar: Char = 2.toChar

  val alias2path: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  val path2alias: mutable.Map[String, String] = new mutable.HashMap[String, String]()

  val alias2location: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  val location2alias: mutable.Map[String, String] = new mutable.HashMap[String, String]()

  var aggregator: mutable.Map[String, String] = new mutable.HashMap[String, String]()

  var clientSeed : Option[String] = None
  var cmdReplySubj : Option[LocalSubj] = None

  override def tickInterval: FiniteDuration = 200.millis



  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Accepted WebSocket connection, proxy actor: $out")
  }

  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  private def messageHandler: Actor.Receive = {
    case Update(sourceRef, subj, data, _) =>
      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("U", path)(Json.stringify(data)))}
    case CommandErr(sourceRef, subj, data) =>
      logger.debug(s"!>>> ${subj2path(subj)}")
      logger.debug(s"!>>> ${path2alias get subj2path(subj)}")
      path2alias get subj2path(subj) foreach { path => logger.info("!>>>>> " + buildClientMessage("U", path)(Json.stringify(data))) }
      logger.debug(s"!>>> ${path2alias get subj2path(subj)}")

      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("U", path)(Json.stringify(data)))}
    case CommandOk(sourceRef, subj, data) =>
      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("U", path)(Json.stringify(data)))}
    case Stale(sourceRef, subj) =>
      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("D", path)())}

    case payload: String =>

      val flag = payload.head
      val d = flag match {
        case 'z' => LZString.decompressFromUTF16(payload.tail)
        case _ => payload.tail
      }

      logger.debug(s"Received from Websocket: $d (${d.length}/${payload.length})")


      d.split(msgSplitChar).foreach { msgContents =>
        val mtype = msgContents.head
        val data = msgContents.tail

        mtype match {
          case 'X' => addUUID(data)
          case 'A' => addOrReplaceAlias(data)
          case 'B' => addOrReplaceLocationAlias(data)
          case _ => extractByAlias(data) foreach { str =>
            extractSubjectAndPayload(str,
              processRequestByType(mtype, _, _) foreach (MessageRouterActor.path ! _)
            )
          }
        }
      }

  }

  private def scheduleOut(path: String, content: String) = aggregator += path -> content

  private def buildClientMessage(mt: String, alias: String)(payload: String = "") = {
    mt + alias + opSplitChar + payload
  }


  private def subj2path(subj: Any) = subj match {
    case RemoteSubj(addr, LocalSubj(ComponentKey(compKey), TopicKey(topicKey))) =>
      mapComponents(compKey).map(segments2path(location2alias.getOrElse(addr, addr), _, topicKey)).getOrElse("invalid")
    case LocalSubj(ComponentKey(compKey), TopicKey(topicKey)) =>
      mapComponents(compKey).map(segments2path("_", _, topicKey)).getOrElse("invalid")
    case _ => "invalid"
  }

  private def segments2path(addr: String, component: String, topic: String) = addr + opSplitChar + component + opSplitChar + topic

  private def addOrReplaceAlias(value: String) = {

    val idx: Int = value.indexOf(opSplitChar)

    val al = value.substring(0, idx)
    val path = value.substring(idx + 1)

    logger.info(s"Alias $al->$path")
    alias2path += al -> path
    path2alias += path -> al
  }

  private def addUUID(value: String) = {

    logger.info(s"UUID $value")

    clientSeed = Some(value)
    cmdReplySubj = Some(LocalSubj(ComponentKey(value), TopicKey("cmd")))

    MessageRouterActor.path ! RegisterComponent(ComponentKey(value), self)

  }

  private def addOrReplaceLocationAlias(value: String) = {

    val idx: Int = value.indexOf(opSplitChar)

    val al = value.substring(0, idx)
    val path = value.substring(idx + 1)

    logger.info(s"Location alias $al->$path")
    alias2location += al -> path
    location2alias += path -> al
  }

  private def processRequestByType(msgType: Char, subj: Subj, payload: Option[JsValue]) = msgType match {
    case 'S' => Some(Subscribe(self, subj))
    case 'U' => Some(Unsubscribe(self, subj))
    case 'C' => Some(Command(self, subj, cmdReplySubj, payload))
    case _ =>
      logger.warn(s"Invalid message type: " + msgType)
      None
  }

  private def extractByAlias(value: String): Option[String] = {
    val idx: Int = value.indexOf(opSplitChar)
    val al = value.substring(0, idx)
    val path = value.substring(idx)

    alias2path.get(al).map(_ + path)
  }

  private def mapComponents(comp: String) : Option[String] = {
    comp match {
      case "_" => None
      case x if clientSeed.isDefined && clientSeed.get == x => Some("_")
      case other => Some(other)
    }
  }

  private def extractSubjectAndPayload(str: String, f: (Subj, Option[JsValue]) => Unit) = {
    def extractPayload(list: List[String]) = list match {
      case Nil => None
      case x :: xs => Json.parse(x).asOpt[JsValue]
    }
    def extract(list: List[String]) = list match {
      case "_" :: comp :: topic :: tail =>
        mapComponents(comp) foreach { mappedComp =>
          f(LocalSubj(ComponentKey(mappedComp), TopicKey(topic)), extractPayload(tail))
        }
      case addr :: comp :: topic :: tail => mapComponents(comp) foreach { mappedComp =>
        alias2location.get(addr).foreach { loc =>
          f(RemoteSubj(loc, LocalSubj(ComponentKey(mappedComp), TopicKey(topic))), extractPayload(tail))
        }
      }
      case _ => logger.warn(s"Invalid payload $str")
    }
    extract(str.split(opSplitChar).toList)
  }

  override def processTick(): Unit = {
    val str = aggregator.values.foldRight("") { (value, aggr) =>
      if (aggr != "") {
        aggr + msgSplitChar + value
      } else {
        value
      }
    }
    if (str.length > 0) {
      var msg = ""
      if (str.length>100) {
        val comp = LZString.compressToUTF16(str)
        if (comp.length<str.length) {
          msg = "z"+comp
        }
      }
      if (msg == "") msg = "f" + str
      out ! msg
      logger.info(s"Sent ${aggregator.size} messages, uncomp ${str.length} comp ${msg.length}")
      aggregator.clear()
    }
  }

}
