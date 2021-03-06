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

import agent.controller.flow.Tools._
import com.typesafe.scalalogging.StrictLogging
import common.{JsonFrame, Fail}
import common.ToolExt.configHelper
import hq.flows.core.Builder.InstructionType
import play.api.libs.json.{JsString, JsValue, Json}

import scalaz.Scalaz._
import scalaz._

private[core] object EnrichProcessorBuilder extends BuilderFromConfig[InstructionType] with StrictLogging {
  val configId = "enrich"

  def field2flow(fieldProps: JsValue): \/[Fail, JsonFrame => JsonFrame] =
    for (
      fieldName <- fieldProps ~> 'name \/> Fail(s"Invalid enrich instruction. Missing 'name' value. Contents: ${Json.stringify(fieldProps)}");
      fieldValue <- fieldProps #> 'value \/> Fail(s"Invalid enrich instruction. Missing 'value' branch. Contents: ${Json.stringify(fieldProps)}")
    ) yield {
      val fieldType = fieldProps ~> 'type | "s"

      frame: JsonFrame => {

        logger.debug(s"Original frame: $frame")

        val keyPath = toPath(macroReplacement(frame, JsString(fieldName)).as[String])

        logger.debug("Key path: {}", keyPath)

        val replacement: JsValue = macroReplacement(frame, fieldValue)

        logger.debug(s"Replacement: $replacement type $fieldType" )

        val value: JsValue = setValue(fieldType, replacement, keyPath, frame.event)

        logger.debug("New frame: {}", value)

        JsonFrame(value, frame.ctx)

      }
    }

  def tags2flow(props: JsValue): \/[Fail, JsonFrame => JsonFrame] =
    (props ##> 'tags | Seq())
      .foldLeft[\/[Fail, JsonFrame => JsonFrame]]({ x: JsonFrame => x}.right) {
      (flow, nextConfigElement) =>
        for (
          aggregatedFlow <- flow;
          nextFlowElement <- field2flow(Json.obj("name" -> "tags", "value" -> nextConfigElement, "type" -> "as"))
        ) yield { frame: JsonFrame =>
          nextFlowElement(aggregatedFlow(frame))
        }
    }

  def fields2flow(props: JsValue): \/[Fail, JsonFrame => JsonFrame] =
    (props ##> 'fields | Seq())
      .foldLeft[\/[Fail, JsonFrame => JsonFrame]]({ x: JsonFrame => x}.right) {
      (flow, nextConfigElement) =>
        for (
          aggregatedFlow <- flow;
          nextFlowElement <- field2flow(nextConfigElement)
        ) yield { frame: JsonFrame =>
          nextFlowElement(aggregatedFlow(frame))
        }
    }


  override def build(props: JsValue): \/[Fail, InstructionType] =
    for (
      tagsLeg <- tags2flow(props);
      fieldsLeg <- fields2flow(props)
    ) yield { frame: JsonFrame =>
      List(tagsLeg(fieldsLeg(frame)))
    }


}
