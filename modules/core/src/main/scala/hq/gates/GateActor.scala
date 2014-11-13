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

package hq.gates

import agent.flavors.files.{Cursor, ProducedMessage}
import agent.shared.{Acknowledge, MessageWithAttachments, Acknowledgeable}
import akka.actor._
import akka.util.ByteString
import common.actors.{PipelineWithStatesActor, SingleComponentActor}
import common.{BecomeActive, BecomePassive}
import hq._
import play.api.libs.json.{JsValue, Json}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}

class GateActor(id: String)
  extends PipelineWithStatesActor
  with SingleComponentActor {

  var active = false

  override def key = ComponentKey("gate/" + id)

  override def preStart(): Unit = {
    context.parent ! GateAvailable(key)
    super.preStart()
  }

  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def becomeActive(): Unit = {
    active = true
    topicUpdate(T_INFO, info)
  }

  override def becomePassive(): Unit = {
    active = false
    topicUpdate(T_INFO, info)
  }

  def info = Some(Json.obj(
    "name" -> id,
    "text" -> s"some random text from $id",
    "state" -> (if (active) "active" else "passive")
  ))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
  }


  override def processTopicCommand(ref: ActorRef, topic: TopicKey, maybeData: Option[JsValue]) = topic match {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the gate")
          self ! BecomePassive()
        case _ =>
          logger.info("Already stopped")
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
        case _ =>
          logger.info("Starting the gate " + self.toString())
          self ! BecomeActive()
      }
    case T_KILL =>
      self ! PoisonPill
  }

  private val messageHandler: Receive = {
    case Acknowledgeable(msg, id) =>
      sender ! Acknowledge(id)
      msg match {
        case ProducedMessage(MessageWithAttachments(msg: ByteString, attachments), c:Cursor) =>
          logger.info(s"Received: ${msg.utf8String}, attachments: ${attachments}, cursor at $c")
      }
      Thread.sleep(3000)

  }

}