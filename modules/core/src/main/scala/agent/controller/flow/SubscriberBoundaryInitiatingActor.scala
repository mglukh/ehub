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

package agent.controller.flow

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import common.actors._

object SubscriberBoundaryInitiatingActor {
  def props(endpoint: String) = Props(new SubscriberBoundaryInitiatingActor(endpoint))
}

class SubscriberBoundaryInitiatingActor(endpoint: String)
  extends PipelineWithStatesActor
  with ShutdownableSubscriberActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[Any] {

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(96) {
    override def inFlightInternally: Int = inFlightCount
  }

  override def connectionEndpoint: String = endpoint

  private def handleOnNext: Actor.Receive = {
    case OnNext(x) =>
      logger.info(s"Next: $x")
      deliverMessage(x)
    case ConnectedState() =>
      logger.info("In connected state")
      switchToCustomBehavior(handleOnNext orElse handleRedeliveryMessages orElse handleReconnectMessages, Some("active"))
    case DisconnectedState() =>
      logger.info("In disconnected state")
      initiateReconnect()
      switchToCustomBehavior(handleOnNext orElse handleReconnectMessages, Some("passive"))
    case x : Acknowledged[_] =>
      logger.info(s"!>>>>> Acknowledged $x")
      context.parent ! x
  }

  override def preStart(): Unit = {
    super.preStart()
  }

  override def receive: Actor.Receive = commonBehavior

  override def becomeActive(): Unit = {
    logger.info(s"Sink becoming active")
    switchToCustomBehavior(handleOnNext orElse handleRedeliveryMessages orElse handleReconnectMessages, Some("active"))
    initiateReconnect()
  }

  override def becomePassive(): Unit = {
    logger.info(s"Sink becoming passive")
    switchToCommonBehavior()
    disconnect()
  }
}
