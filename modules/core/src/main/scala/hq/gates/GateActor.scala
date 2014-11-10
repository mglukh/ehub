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

import akka.actor._
import common.{BecomeActive, BecomePassive}
import common.actors.{ActorWithSubscribers, PipelineWithStatesActor}
import hq._
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsValue, Json}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}

class GateActor(id: String) extends PipelineWithStatesActor with ActorWithSubscribers {

  def route = "gate/"+id

  val GATE_X_INFO = Subject(route, "info")

  val GATE_X_START = Subject(route, "start")
  val GATE_X_STOP = Subject(route, "stop")
  val GATE_X_KILL = Subject(route, "kill")

  var active = false

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! GateAvailable(route)
  }

  override def commonBehavior(): Actor.Receive = super.commonBehavior()

  override def becomeActive(): Unit = {
    active = true
    updateToAll()
  }

  override def becomePassive(): Unit = {
    active = false
    updateToAll()
  }

  def updateToAll():Unit = updateToAll(GATE_X_INFO, info)

  def info = Some(Json.obj(
    "name" -> id,
    "text" -> s"some random text from $id",
    "state" -> (if (active) "active" else "passive")
  ))

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case GATE_X_INFO => updateTo(subject, ref, info)
  }


  override def processCommand(ref: ActorRef, subject: Subject, maybeData: Option[JsValue]) = subject.topic match {
    case "stop" =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the gate")
          self ! BecomePassive()
        case _ =>
          logger.info("Already stopped")
      }
    case "start" =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
        case _ =>
          logger.info("Starting the gate")
          self ! BecomeActive()
      }
    case "kill" =>
      self ! PoisonPill
  }

}