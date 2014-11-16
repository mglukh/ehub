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

package hq.flows.core

import agent.shared.{Acknowledge, Acknowledgeable}
import akka.actor.{ActorRefFactory, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Request
import common.{JsonFrame, Fail}
import common.ToolExt.configHelper
import common.actors.ActorWithComposableBehavior
import hq.flows.core.Builder.SourceActorPropsType
import hq.gates.RegisterSink
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

private[core] object GateInputBuilder extends BuilderFromConfig[SourceActorPropsType] {
  val configId = "gate"

  override def build(props: JsValue): \/[Fail, SourceActorPropsType] =
    for (
      name <- props ~> 'name \/> Fail(s"Invalid gate input configuration. Missing 'name' value. Contents: ${Json.stringify(props)}")
    ) yield GateActor.props(name)
}

private object GateActor {
  def props(name: String) = Props(new GateActor(name))

  def start(name: String)(implicit f: ActorRefFactory) = f.actorOf(props(name))
}

private class GateActor(name: String) extends ActorPublisher[JsonFrame] with ActorWithComposableBehavior {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"About to start tap $name")
    // TODO do it properly
    context.actorSelection("/user/gates/" + name) ! RegisterSink(self)
  }

  def handler: Receive = {
    case m @ Acknowledgeable(f:JsonFrame,i) =>
      if (totalDemand > 0) {
        logger.debug(s"New message at gate tap (demand $totalDemand) [$name]: $m - produced and acknowledged")
        onNext(f)
        sender ! Acknowledge(i)
      } else {
        logger.debug(s"New message at gate tap (demand $totalDemand) [$name]: $m - ignored, no demand")
      }
    case m: Acknowledgeable[_] => logger.warn(s"Unexpected message at tap [$name]: $m - ignored")
    case Request(n) => logger.debug(s"Downstream requested $n messages")
  }

}

