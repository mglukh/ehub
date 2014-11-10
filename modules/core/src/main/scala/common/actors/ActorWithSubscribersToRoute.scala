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
import hq.{RegisterComponent, Subject}
import play.api.libs.json.JsValue

trait ActorWithSubscribersToRoute
  extends ActorWithSubscribers {

  def route: String

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent(route, self)
    super.preStart()
  }

  def topicUpdate(topic: String, data: Option[JsValue], singleTarget: Option[ActorRef] = None) =
    singleTarget match {
      case Some(ref) => updateTo(Subject(myAddress, route, topic), ref, data)
      case None => updateToAll(Subject(myAddress, route, topic), data)
    }

  def processTopicSubscribe(ref: ActorRef, topic: String): Unit = {}

  def processTopicUnsubscribe(ref: ActorRef, topic: String): Unit = {}

  override final def processSubscribeRequest(ref: ActorRef, subject: Subject) = processTopicSubscribe(ref, subject.topic)

  override final def processUnsubscribeRequest(ref: ActorRef, subject: Subject) = processTopicUnsubscribe(ref, subject.topic)
}
