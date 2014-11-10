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
import common.actors.{ActorWithComposableBehavior, ActorWithSubscribers}
import hq._
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsValue, Json}


object GateManagerActor {
  def id = "gates"

  def props = Props(new GateManagerActor())

  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)
}

case class GateAvailable(id: String)

class GateManagerActor extends ActorWithComposableBehavior
with ActorWithSubscribers {

  val GATES_LIST = Subject("gates", "list")

  var gates: Map[String, ActorRef] = Map()

  override def commonBehavior(): Actor.Receive = handler orElse super.commonBehavior()

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent("gates", self)
  }


  def list = Some(Json.toJson(gates.keys.map { x => Json.obj("id" -> x)}.toArray))

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case GATES_LIST => updateTo(subject, ref, list)
  }


  override def processCommand(ref: ActorRef, subject: Subject, maybeData: Option[JsValue]) = subject.topic match {
    case "add" =>
      for (
        data <- maybeData;
        name <- (data \ "name").asOpt[String]
      ) {
        val actor = GateActor.start(name)
        context.watch(actor)
      }
  }


  def handler: Receive = {
    case GateAvailable(route) =>
      gates = gates + (route -> sender())
      updateToAll(GATES_LIST, list)
    case Terminated(ref) =>
      gates = gates.filter {
        case (route,otherRef) => otherRef != ref
      }
      updateToAll(GATES_LIST, list)
  }


}
