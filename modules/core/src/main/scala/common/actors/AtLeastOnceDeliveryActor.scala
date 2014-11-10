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

import agent.shared.{Acknowledge, Acknowledgeable}
import common.NowProvider

import scala.concurrent.duration.DurationInt

case class Acknowledged[T](correlationId: Long, msg: T)


trait AtLeastOnceDeliveryActor[T] extends ActorWithTicks with WithRemoteActorRef with NowProvider {

  private var list = Vector[InFlight]()

  private var counter = now

  def inFlightCount = list.size
  def configUnacknowledgedMessagesResendInterval = 5.seconds

  private def correlationId(m:T) : Long = {
    counter = counter + 1
    counter
  }

  private def resendAllPending() = {
    if (remoteActorRef.isDefined && list.nonEmpty) {
      logger.debug(s"Resending pending messages. Total inflight: $inFlightCount")
      list = for (
        next <- list
      ) yield resend(next)
    }
  }

  private def resend(m: InFlight) : InFlight = {
    if (now - m.sentTime > configUnacknowledgedMessagesResendInterval.toMillis) {
      logger.debug(s"Resending ${m.correlationId}")
      send(m)
    } else m
  }

  private def send(m: InFlight) : InFlight = {
    val sentTime = remoteActorRef match {
      case None => m.sentTime
      case Some(ref) =>
        ref ! Acknowledgeable(m.msg, m.correlationId)
        now
    }
    InFlight(sentTime, m.msg, m.correlationId)
  }

  def deliverMessage(msg: T) = {
    list = list :+ send(InFlight(0, msg, correlationId(msg)))
  }

  def acknowledgeUpTo(correlationId: Long) = {
    logger.debug(s"Ack: $correlationId")
    list.find(_.correlationId == correlationId).foreach( v => self ! Acknowledged[T](correlationId, v.msg) )
    list = list.dropWhile(_.correlationId <= correlationId)
  }

  override def processTick() = {
    resendAllPending()
  }

  def handleRedeliveryMessages : Receive = {
    case Acknowledge(x) => acknowledgeUpTo(x)
  }

  private case class InFlight(sentTime: Long, msg: T, correlationId: Long)


}
