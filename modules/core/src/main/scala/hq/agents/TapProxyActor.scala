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

package hq.agents

import agent.shared._
import akka.actor._
import akka.remote.DisassociatedEvent
import common.actors.{ActorWithSubscribers, PipelineWithStatesActor}
import hq.{RegisterComponent, Subject}
import hq.routing.MessageRouterActor
import play.api.libs.json.{JsArray, JsValue, Json}


object TapProxyActor {
  def start(parentRoute: String, id: String, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(parentRoute, id, ref), id)

  def props(parentRoute: String, id: String, ref: ActorRef) = Props(new TapProxyActor(parentRoute, id, ref))
}

class TapProxyActor(parentRoute: String, id: String, ref: ActorRef) extends PipelineWithStatesActor with ActorWithSubscribers {

  val route = parentRoute + "/" + id
  val AGENT_X_TAP_X_INFO = Subject(route, "info")

  private var info: Option[JsValue] = None

  override def commonBehavior(): Actor.Receive = commonMessageHandler orElse super.commonBehavior()

  private def commonMessageHandler: Receive = {
    case GenericJSONMessage(jsonString) =>
      Json.parse(jsonString).asOpt[JsValue] foreach process
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        self ! PoisonPill
      }
  }

  private def process(json: JsValue) = {
    (json \ "info").asOpt[JsValue] foreach processInfo
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received tap info update: $info")
    updateToAll(AGENT_X_TAP_X_INFO, info)
  }

  override def preStart(): Unit = {
    super.preStart()
    ref ! CommunicationProxyRef(self)
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! TapAvailable(route)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
  }


  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENT_X_TAP_X_INFO => updateTo(subject, ref, info)
  }

  override def processCommand(ref: ActorRef, subject: Subject, maybeData: Option[JsValue]) = subject.topic match {
    case "start" => ref ! OpenTap()
    case "stop" => ref ! CloseTap()
    case "kill" => ref ! RemoveTap()
  }


}
