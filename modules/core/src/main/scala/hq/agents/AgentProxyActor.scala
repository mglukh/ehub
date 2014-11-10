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

import agent.shared.{CreateTap, CommunicationProxyRef, GenericJSONMessage}
import akka.actor._
import akka.remote.DisassociatedEvent
import common.actors.{ActorObj, ActorObjWithoutConfig, ActorWithSubscribers, PipelineWithStatesActor}
import hq.routing.MessageRouterActor
import hq.{RegisterComponent, Subject}
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.duration.DurationLong
import scala.util.{Failure, Success}

/**
 * Created by maks on 5/11/2014.
 */
object AgentProxyActor {
  def start(id: String, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(id, ref), id)

  def props(id: String, ref: ActorRef) = Props(new AgentProxyActor(id, ref))
}

case class TapAvailable(id: String)


class AgentProxyActor(id: String, ref: ActorRef) extends PipelineWithStatesActor with ActorWithSubscribers {
  val route = "agent/" + id
  val AGENT_X_INFO = Subject(route, "info")
  val AGENT_X_TAPS = Subject(route, "taps")
  val AGENT_X_ADD_TAP = Subject(route, "addTap")


  private var info: Option[JsValue] = None

  private var activeTaps: Set[String] = Set()
  private var confirmedTaps: List[String] = List()


  override def commonBehavior(): Actor.Receive = commonMessageHandler orElse super.commonBehavior()

  private def commonMessageHandler: Receive = {
    case TapAvailable(x) =>
      confirmedTaps = confirmedTaps :+ x
      updateToAll(AGENT_X_TAPS, taps)
    case GenericJSONMessage(jsonString) =>
      Json.parse(jsonString).asOpt[JsValue] foreach process
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        self ! PoisonPill
      }
  }


  private def taps = Some(Json.toJson(confirmedTaps.map { x => Json.obj("id" -> x)}))

  private def process(json: JsValue) = {
    (json \ "info").asOpt[JsValue] foreach processInfo
    (json \ "taps").asOpt[JsArray] foreach processListOfTaps
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received agent info update: $info")
    updateToAll(AGENT_X_INFO, info)
  }

  private def processListOfTaps(json: JsArray) = {
    logger.debug(s"Received taps update: $json")

    val listOfIds = for (
      tapDetails <- json.value;
      id <- (tapDetails \ "id").asOpt[String];
      ref <- (tapDetails \ "ref").asOpt[String]
    ) yield id -> ref

    activeTaps filterNot listOfIds.contains foreach killTapProxy
    listOfIds.filterNot {
      case (i,r) => activeTaps.contains(i)
    } foreach {
      case (i,r) => createTapProxy(i,r)
    }
  }

  private def createTapProxy(id: String, ref: String) = {
    context.actorSelection(ref).resolveOne(5.seconds).onComplete {
      case Success(result) =>
        activeTaps += id
        TapProxyActor.start(route, id, result)
      case Failure(failure) => logger.warn(s"Unable to resolve $id at $ref", failure)
    }
  }

  private def killTapProxy(id: String) = {
    context.child(id).foreach(_ ! PoisonPill)
  }

  override def preStart(): Unit = {
    super.preStart()
    ref ! CommunicationProxyRef(self)
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! AgentAvailable(route)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
  }


  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case AGENT_X_INFO => updateTo(subject, ref, info)
    case AGENT_X_TAPS => updateTo(subject, ref, taps)
  }

  override def processCommand(ref: ActorRef, subject: Subject, maybeData: Option[JsValue]) = subject.topic match {
    case "addTap" =>
      maybeData.foreach(ref ! CreateTap(_))
  }


}