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

import agent.shared.{GateStateUpdate, GateOpen, GateState, GateStateCheck}
import common.NowProvider

import scala.concurrent.duration.DurationDouble

trait ActorWithGateStateMonitoring
  extends ActorWithTicks
  with WithRemoteActorRef
  with NowProvider {


  override def commonBehavior: Receive = mHandler orElse super.commonBehavior

  private var checkStateOn = false
  private var lastCheck: Option[Long] = None
  private var lastKnownState: Option[GateState] = None

  def gateStateCheckInterval = 10.seconds

  def isGateOpen = lastKnownState match {
    case Some(GateOpen()) => true
    case _ => false
  }

  def isGateClosed = !isGateOpen

  def startGateStateMonitoring() = {
    checkStateOn = true
  }

  def stopGateStateMonitoring() = {
    checkStateOn = false
  }

  override def internalProcessTick(): Unit = {
    if (checkStateOn) sendCheck()
    super.internalProcessTick()
  }

  private def waitPeriodPassed(time: Long) = time + gateStateCheckInterval.toMillis < now

  private def sendCheck() = {
    lastKnownState match {
      case Some(GateOpen()) => // nothing to do if gate is open
      case _ => lastCheck match {
        case Some(time) if !waitPeriodPassed(time) => () // within waiting period
        case _ =>
          lastCheck = Some(now)
          remoteActorRef match {
            case Some(ref) =>
              logger.info(s"State check -> $ref")
              ref ! GateStateCheck(self)
            case None => // disconnected, nothing to do
          }
      }
    }
  }

  def onGateStateChanged(state: GateState): Unit = {}

  private def mHandler : Receive = {
    case GateStateUpdate(state) =>
      lastKnownState match {
        case Some(lastState) if lastState == state =>
          logger.info(s"Received state update, no change, still $state")
          // no state change, do nothing..
        case _ =>
          lastKnownState = Some(state)
          logger.debug(s"Gate $remoteActorRef state changed to $state")
          onGateStateChanged(state)
      }
  }

}
