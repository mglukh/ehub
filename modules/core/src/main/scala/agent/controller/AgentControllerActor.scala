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

package agent.controller

import agent.controller.flow.{TapActor, TapConfigUpdate, StartFlowInstance, SuspendFlowInstance}
import agent.controller.storage._
import agent.shared._
import akka.actor.{ActorRef, Props}
import akka.stream.scaladsl2.FlowMaterializer
import com.typesafe.config.Config
import common.actors.{ActorObjWithConfig, ActorObj, ActorWithComposableBehavior, ReconnectingActor}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

/**
 * Created by maks on 18/09/14.
 */
object AgentControllerActor extends ActorObjWithConfig {
  override def id: String = "controller"
  override def props(implicit config: Config) = Props(new AgentControllerActor())
}

class AgentControllerActor(implicit config: Config)
  extends ActorWithComposableBehavior
  with ReconnectingActor {

  implicit val mat = FlowMaterializer()
  implicit val system = context.system

  val tapActors: mutable.Map[Long, ActorRef] = mutable.HashMap()
  var storage = ConfigStorageActor.path
  var commProxy: Option[ActorRef] = None

  override def commonBehavior(): Receive = commonMessageHandler orElse super.commonBehavior()

  private def commonMessageHandler: Receive = handleReconnectMessages orElse {
    case ConnectedState() =>
      remoteActorRef.foreach(_ ! Handshake(self, config.as[String]("agent.name")))
    case DisconnectedState() =>
      commProxy = None
      initiateReconnect()
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
  }

  private def nextAvailableId = tapActors.keys.max + 1

  private def sendToHQAll() = {
    sendToHQ(info)
    sendToHQ(snapshot)
  }

  private def sendToHQ(json: JsValue) = {
    commProxy foreach { actor =>
      logger.debug(s"$json -> $actor")
      actor ! GenericJSONMessage(json.toString())
    }
  }

  private def snapshot = Json.obj(
    "taps" -> Json.toJson(
      tapActors.keys.toArray.sorted.map { key => Json.obj(
        "id" -> actorFriendlyId(key.toString),
        "ref" -> tapActors.get(key).get.path.toString
      )}
    )
  )

  private def info = Json.obj(
    "info" -> Json.obj(
      "name" -> config.as[String]("agent.name"),
      "description" -> config.as[String]("agent.description"),
      "location" -> config.as[String]("agent.location"),
      "address" -> context.self.path.address.toString,
      "state" -> "active"
    )
  )


  override def connectionEndpoint: String = config.as[String]("agent.hq.endpoint")

  override def preStart(): Unit = {
    initiateReconnect()
    storage ! RetrieveConfigForAll()
    super.preStart()
    switchToCustomBehavior(handleInitialisationMessages, Some("awaiting initialisation"))
  }

  private def handleInitialisationMessages: Receive = {
    case StoredConfigs(list) =>
      logger.debug(s"Received list of tap from the storage: $list")
      list.foreach {
        case StoredConfig(id, Some(TapConfig(_, cfg, state))) =>
          createActor(id, cfg, state)
        case StoredConfig(id, None) =>
          logger.warn(s"No config defined for tap ID $id")
      }
      switchToCustomBehavior(handleTapOpMessages, Some("existing taps loaded"))
  }

  private def handleTapOpMessages: Receive = {

    case CreateTap(cfg) =>
      val tapId = (cfg \ "tapId").asOpt[Long] getOrElse nextAvailableId
      val cfgAsStr: String = Json.stringify(cfg)
      logger.info("Creating tap " + tapId)
      storage ! StoreConfig(TapConfig(tapId, cfgAsStr, None))
      createActor(tapId, cfgAsStr, None)
//    case OpenTap(id) =>
//      logger.info("Starting flow " + id)
//      tapActors.get(id).foreach(_ ! StartFlowInstance())
//    case CloseTap(id) =>
//      logger.info("Stopping flow " + id)
//      tapActors.get(id).foreach(_ ! SuspendFlowInstance())
    case TapConfigUpdate(id, state) =>
      logger.info(s"Tap state update: id=$id state=$state")
      storage ! StoreState(TapState(id, Some(Json.stringify(state))))
  }

  def createActor(tapId: Long, config: String, maybeState: Option[String]): Unit = {
    val actorId = actorFriendlyId(tapId.toString)
    tapActors.get(tapId).foreach { actor =>
      logger.info(s"Stopping $actor")
      context.stop(actor)
    }
    logger.info(s"Creating a new actor for tap $tapId")
    tapActors += (tapId -> context.actorOf(TapActor.props(tapId, Json.parse(config), maybeState.map(Json.parse)), actorId))
    sendToHQ(snapshot)
  }


}