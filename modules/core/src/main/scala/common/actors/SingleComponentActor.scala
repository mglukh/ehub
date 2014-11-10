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

import akka.actor.ActorRef
import hq.routing.MessageRouterActor
import hq._
import play.api.libs.json.JsValue

trait SingleComponentActor
  extends ActorWithLocalSubscribers {

  val T_ADD = TopicKey("add")
  val T_LIST = TopicKey("list")
  val T_INFO = TopicKey("info")
  val T_START = TopicKey("start")
  val T_STOP = TopicKey("stop")
  val T_KILL = TopicKey("kill")


  def key: ComponentKey

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent(key, self)
    super.preStart()
  }

  def topicUpdate(topic: TopicKey, data: Option[JsValue], singleTarget: Option[ActorRef] = None) =
    singleTarget match {
      case Some(ref) => updateTo(LocalSubj(key, topic), ref, data)
      case None => updateToAll(LocalSubj(key, topic), data)
    }

  def processTopicSubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {}

  def processTopicUnsubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {}

  def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, maybeData: Option[JsValue]): Unit = {}

  override def processSubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = processTopicSubscribe(sourceRef, subject.topic)

  override def processUnsubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = processTopicUnsubscribe(sourceRef, subject.topic)

  override def processCommand(sourceRef: ActorRef, subject: LocalSubj, maybeData: Option[JsValue]): Unit = {
   logger.info(s"!>>>> received command for ${subject.topic} from $sourceRef")
    processTopicCommand(sourceRef, subject.topic, maybeData)
  }
}
