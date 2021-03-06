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

package common.actors

import akka.actor.Actor
import common.{BecomePassive, BecomeActive}


trait PipelineWithStatesActor extends ActorWithComposableBehavior {

  private var requestedState: Option[RequestedState] = None

  def lastRequestedState = requestedState

  def isPipelineActive = requestedState match {
    case Some(Active()) => true
    case _ => false
  }
  def isPipelinePassive = !isPipelineActive

  def becomeActive(): Unit = {}

  def becomePassive(): Unit = {}

  override def commonBehavior: Actor.Receive = handlePipelineStateChanges orElse super.commonBehavior

  private def handlePipelineStateChanges: Actor.Receive = {
    case BecomeActive() =>
      logger.debug("Becoming active")
      requestedState = Some(Active())
      becomeActive()
    case BecomePassive() =>
      logger.debug("Becoming passive")
      requestedState = Some(Passive())
      becomePassive()
  }

  sealed trait RequestedState

  case class Active() extends RequestedState

  case class Passive() extends RequestedState

}
