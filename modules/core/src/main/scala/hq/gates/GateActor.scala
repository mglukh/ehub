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
import agent.shared._
import akka.actor._
import akka.util.ByteString
import common.actors.{ActorWithComposableBehavior, AtLeastOnceDeliveryActor, PipelineWithStatesActor, SingleComponentActor}
import common.{BecomeActive, BecomePassive}
import hq._
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}

object SuperSlowTempConsumer {
  def props(ref: ActorRef) = Props(new SuperSlowTempConsumer(ref))

  def start(ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(ref))
}

class SuperSlowTempConsumer(ref: ActorRef) extends ActorWithComposableBehavior {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    ref ! RegisterSink(self)
  }

  def handler: Receive = {
    case Acknowledgeable(msg, id) =>
      Thread.sleep(15000)
      sender ! Acknowledge(id)
      msg match {
        case Acknowledgeable(ProducedMessage(MessageWithAttachments(msg: ByteString, attachments), c: Cursor),_) =>
          logger.info(s"Received (super slow): ${msg.utf8String}, attachments: $attachments, cursor at $c")
      }
  }
}

object MediumSlowTempConsumer {
  def props(ref: ActorRef) = Props(new MediumSlowTempConsumer(ref))

  def start(ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(ref))
}

class MediumSlowTempConsumer(ref: ActorRef) extends ActorWithComposableBehavior {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    ref ! RegisterSink(self)
  }

  def handler: Receive = {
    case Acknowledgeable(msg, id) =>
      Thread.sleep(1000)
      sender ! Acknowledge(id)
      msg match {
        case Acknowledgeable(ProducedMessage(MessageWithAttachments(msg: ByteString, attachments), c: Cursor),_) =>
          logger.info(s"Received (avg slow): ${msg.utf8String}, attachments: $attachments, cursor at $c")
      }
  }
}


case class RegisterSink(sinkRef: ActorRef)


class GateActor(id: String)
  extends PipelineWithStatesActor
  with AtLeastOnceDeliveryActor[Acknowledgeable[_]]
  with SingleComponentActor {


  override def configUnacknowledgedMessagesResendInterval: FiniteDuration = 10.seconds

  private val correlationToOrigin: mutable.Map[Long, ActorRef] = mutable.Map()
  private var sinks: Set[ActorRef] = Set()

  override def key = ComponentKey("gate/" + id)

  override def preStart(): Unit = {

    // TODO remove
    SuperSlowTempConsumer.start(self)
    MediumSlowTempConsumer.start(self)

    context.parent ! GateAvailable(key)
    super.preStart()
  }

  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def becomeActive(): Unit = {
    topicUpdate(T_INFO, info)
  }

  override def becomePassive(): Unit = {
    topicUpdate(T_INFO, info)
  }

  def info = Some(Json.obj(
    "name" -> id,
    "text" -> s"some random text from $id",
    "state" -> (if (isPipelineActive) "active" else "passive")
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

  override def canDeliverDownstreamRightNow: Boolean = isPipelineActive

  override def fullyAcknowledged(correlationId: Long, msg: Acknowledgeable[_]): Unit = {
    logger.info(s"Fully acknowledged $correlationId / ${msg.id}")
    correlationToOrigin.get(msg.id).foreach { origin =>
      logger.info(s"Ack ${msg.id} with tap")
      origin ! Acknowledge(msg.id)
    }
  }

  override def getSetOfActiveEndpoints: Set[ActorRef] = sinks

  private def messageHandler: Receive = {
    case GateStateCheck(ref) =>
      logger.debug(s"Received state check from $ref, our state: $isPipelineActive")
      if (isPipelineActive) {
        ref ! GateStateUpdate(GateOpen())
      } else {
        ref ! GateStateUpdate(GateClosed())
      }
    case RegisterSink(sinkRef) =>
      sinks += sender()
      logger.info(s"New sink: ${sender()}")
    case b: Acknowledgeable[_] =>
      if (isPipelineActive) {
        logger.info(s"New message arrived at the gate... ${b.id}")
        if (!correlationToOrigin.contains(b.id)) {
          correlationToOrigin += b.id -> sender()
          deliverMessage(b)
        } else {
          logger.info(s"Received duplicate message ${b.id}")
        }
      } else {
        sender ! GateStateUpdate(GateClosed())
      }
  }

}

