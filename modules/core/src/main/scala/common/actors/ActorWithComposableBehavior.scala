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

import com.typesafe.scalalogging.StrictLogging

trait ActorWithComposableBehavior extends ActorUtils with StrictLogging {

  def commonBehavior: Receive = {
    case msg: Loggable => logger.info(String.valueOf(msg))
  }

  final def switchToCustomBehavior(customBehavior: Receive, bid: Option[String] = None) = {
    logger.debug(s"Switched to custom behavior, id=$bid")
    context.become(customBehavior orElse commonBehavior)
  }

  final def switchToCommonBehavior() = {
    logger.debug("Switched to common behavior")
    context.become(commonBehavior)
  }

  override def receive: Receive = commonBehavior

}
