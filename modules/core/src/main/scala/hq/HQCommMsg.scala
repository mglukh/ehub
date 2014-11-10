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

package hq

import akka.actor.ActorRef
import play.api.libs.json.JsValue

trait HQCommMsg[T <: SubscriptionKey] {
  val key: T
}

sealed trait SubscriptionKey
case class TopicKey(topic: String) extends SubscriptionKey
case class ComponentKey(route: String, topic: TopicKey) extends SubscriptionKey
case class RemoteComponentKey(address: String, component: ComponentKey) extends SubscriptionKey

case class Subscribe[T <: SubscriptionKey](key: T) extends HQCommMsg[T]
case class Unsubscribe[T <: SubscriptionKey](key: T) extends HQCommMsg[T]
case class Command[T <: SubscriptionKey](key: T, data: Option[JsValue] = None) extends HQCommMsg[T]
case class Update[T <: SubscriptionKey](key: T, data: JsValue, canBeCached: Boolean = true) extends HQCommMsg[T]
case class Stale[T <: SubscriptionKey](key: T) extends HQCommMsg[T]

case class RegisterComponent(route: String, ref: ActorRef)