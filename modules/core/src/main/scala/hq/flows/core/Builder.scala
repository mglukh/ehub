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

import akka.actor.{ActorRefFactory, Props}
import akka.stream.actor.{ActorSubscriber, ActorPublisher}
import akka.stream.scaladsl._
import com.typesafe.scalalogging.StrictLogging
import common.{JsonFrame, Fail}
import common.ToolExt._
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz.{-\/, \/, \/-}

object Builder extends StrictLogging {

  type SourceActorPropsType = Props
  type SourceType = Source[JsonFrame]
  type SinkActorPropsType = Props
  type SinkType = Sink[JsonFrame]
  type InstructionType = (JsonFrame) => scala.collection.immutable.Seq[JsonFrame]
  type ProcessorFlowType = Flow[JsonFrame, JsonFrame]
  type FlowType = RunnableFlow

  def apply()(implicit config: JsValue, f: ActorRefFactory): \/[Fail, FlowType] =
    for (
      tap <- buildTap;
      pipeline <- buildProcessingPipeline;
      sink <- buildSink
    ) yield tap.via(pipeline).to(sink)


  def buildTap(implicit config: JsValue, f: ActorRefFactory): \/[Fail, SourceType] = {
    val allBuilders = Seq(GateInputBuilder)
    for (
      input <- config #> 'tap \/> Fail("Invalid config: missing 'tap' branch");
      inputClass <- input ~> 'class \/> Fail("Invalid input config: missing 'class' value");
      inputProps <- input #> 'props \/> Fail("Invalid input config: missing 'props' branch");
      builder <- allBuilders.find(_.configId == inputClass)
        \/> Fail(s"Unsupported or invalid input class $inputClass. Supported classes: ${allBuilders.map(_.configId)}");
      tap <- builder.build(inputProps)
    ) yield PublisherSource(ActorPublisher[JsonFrame](f.actorOf(tap)))
    
  }

  def buildSink(implicit config: JsValue, f: ActorRefFactory): \/[Fail, SinkType] = {
    val allBuilders = Seq(BlackHoleSinkBuilder, GateSinkBuilder)
    for (
      sink <- config #> 'sink \/> Fail("Invalid config: missing 'sink' branch");
      sinkClass <- sink ~> 'class \/> Fail("Invalid sink config: missing 'class' value");
      sinkProps <- sink #> 'props \/> Fail("Invalid sink config: missing 'props' branch");
      builder <- allBuilders.find(_.configId == sinkClass)
        \/> Fail(s"Unsupported or invalid sink class $sinkClass. Supported classes: ${allBuilders.map(_.configId)}");
      sink <- builder.build(sinkProps)
    ) yield SubscriberSink(ActorSubscriber(f.actorOf(sink)))
  }

  def buildInstruction(implicit config: JsValue): \/[Fail, InstructionType] = {
    val allBuilders = Seq(
      EnrichProcessorBuilder,
      GrokProcessorBuilder,
      LogProcessorBuilder,
      DropProcessorBuilder
    )
    for (
      instClass <- config ~> 'class \/> Fail("Invalid instruction config: missing 'class' value");
      instProps <- config #> 'props \/> Fail("Invalid instruction config: missing 'props' branch");
      builder <- allBuilders.find(_.configId == instClass)
        \/> Fail(s"Unsupported or invalid instruction class $instClass. Supported classes: ${allBuilders.map(_.configId)}");
      instr <- builder.build(instProps)
    ) yield instr
  }

  def buildProcessingPipeline(implicit config: JsValue): \/[Fail, ProcessorFlowType] =
    for (
      instructions <- config ##> 'pipeline \/> Fail("Invalid pipeline config: missing 'pipeline' value");
      folded <- instructions
        .foldLeft[\/[Fail, ProcessorFlowType]](\/-(Flow[JsonFrame])) { (aggr, json) =>
        for (
          flow <- aggr;
          nextStep <- buildInstruction(json);
          condition <- Condition(json #> 'condition)
        ) yield flow.via(Flow[JsonFrame].mapConcat[JsonFrame] { frame =>
          condition.metFor(frame) match {
            case -\/(fail) =>
              logger.debug("Condition failed: " + fail)
              List(frame)
            case \/-(_) =>
              nextStep(frame)
          }
        })
      }
    ) yield folded


}
