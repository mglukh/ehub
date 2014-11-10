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

package agent.controller.flow

import java.nio.charset.Charset

import agent.flavors.files._
import agent.shared.MessageWithAttachments
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl2._
import akka.util.ByteString
import common.{BecomeActive, BecomePassive}
import common.actors.{Acknowledged, ActorWithComposableBehavior}
import play.api.libs.json._
import play.api.libs.json.extensions._


object TapActor {
  def props(flowId: Long, config: JsValue, state: Option[JsValue])(implicit mat: FlowMaterializer, system: ActorSystem) = Props(new TapActor(flowId, config, state))
}

case class StartFlowInstance()

case class SuspendFlowInstance()

case class TapConfigUpdate(flowId: Long, config: JsValue)

class TapActor(flowId: Long, config: JsValue, state: Option[JsValue])(implicit mat: FlowMaterializer) extends ActorWithComposableBehavior {

  type In = ProducedMessage[ByteString, Cursor]
  type Out = ProducedMessage[MessageWithAttachments[ByteString], Cursor]

  private var flow: Option[FlowInstance] = None

  case class FlowInstance(flow: MaterializedFlow, source: ActorRef, sink: ActorRef)

  private var cursor2config: Option[Cursor => Option[JsValue]] = None

  private def createFlowFromConfig(): Unit = {

    import play.api.libs.json.Reads._
    import play.api.libs.json._

    logger.info(s"Creating flow $flowId, config $config, initial state $state")

    implicit val dispatcher = context.system.dispatcher
    implicit val mat = FlowMaterializer()


    def buildProcessorFlow(props: JsValue): ProcessorFlow[In, Out] = {
      val convert = FlowFrom[In].map {
        case ProducedMessage(msg, c) =>
          ProducedMessage(MessageWithAttachments(msg, Json.obj()), c)
      }

      val setSource = FlowFrom[Out].map {
        case ProducedMessage(MessageWithAttachments(msg, json), c) =>
          val modifiedJson = json set __ \ "sourceId" -> JsString((props \ "sourceId").asOpt[String].getOrElse("undefined"))
          ProducedMessage(MessageWithAttachments(msg, modifiedJson), c)
      }

      val setTags = FlowFrom[Out].map {
        case ProducedMessage(MessageWithAttachments(msg, json), c) =>
          val modifiedJson = json set __ \ "tags" -> (props \ "tags").asOpt[JsArray].getOrElse(Json.arr())
          ProducedMessage(MessageWithAttachments(msg, modifiedJson), c)
      }

      convert.append(setSource).append(setTags)
    }


    def buildFileMonitorTarget(targetProps: JsValue): MonitorTarget = {

      logger.debug(s"Creating RollingFileMonitorTarget from $targetProps")

      RollingFileMonitorTarget(
        (targetProps \ "directory").as[String],
        (targetProps \ "mainPattern").as[String],
        (targetProps \ "rollPattern").as[String],
        f => f.lastModified())
    }

    def buildState() : Option[Cursor] = {
      for (
        stateCfg <- state;
        fileCursorCfg <- (stateCfg \ "fileCursor").asOpt[JsValue];
        seed <- (fileCursorCfg \ "idx" \ "seed").asOpt[Long];
        resourceId <- (fileCursorCfg \ "idx" \ "rId").asOpt[Long];
        positionWithinItem <- (fileCursorCfg \ "pos" ).asOpt[Long]
      ) yield {
        val state = FileCursor(ResourceIndex(seed, resourceId), positionWithinItem)
        logger.info(s"Initial state: $state")
        state
      }
    }

    def buildFileProducer(fId: Long, props: JsValue): Props = {
      implicit val charset = Charset.forName("UTF-8")
      implicit val fileIndexing = new FileIndexer

      logger.debug(s"Building FileMonitorActorPublisher from $props")

      cursor2config = Some {
        case FileCursor(ResourceIndex(seed, resourceId), positionWithinItem) =>
          Some(Json.obj(
            "fileCursor" -> Json.obj(
              "idx" -> Json.obj(
                "seed" -> seed,
                "rId" -> resourceId),
              "pos" -> positionWithinItem)))
        case _ => None
      }

      FileMonitorActorPublisher.props(fId, buildFileMonitorTarget(props \ "target"), buildState())
    }

    def buildProducer(fId: Long, config: JsValue): Props = {

      logger.debug(s"Building producer from $config")

      (config \ "class").asOpt[String] match {
        case Some("file") => buildFileProducer(fId, config \ "props")
        case _ => throw new IllegalArgumentException(config.toString())
      }
    }


    def buildAkkaSink(fId: Long, props: JsValue): Props = {
      SubscriberBoundaryInitiatingActor.props((props \ "url").as[String])
    }

    def buildSink(fId: Long, config: JsValue): Props = {
      (config \ "class").asOpt[String] match {
        case Some("akka") => buildAkkaSink(fId, config \ "props")
        case _ => BlackholeAutoAckSinkActor.props
      }
    }



    val publisherProps = buildProducer(flowId, config \ "source")
    val publisherActor: ActorRef = context.actorOf(publisherProps)
    val publisher = PublisherSource(ActorPublisher[In](publisherActor))

    val processingSteps = buildProcessorFlow(config \ "preprocessing")

    val sinkProps = buildSink(flowId, config \ "endpoint")
    val sinkActor = context.actorOf(sinkProps)
    val sink = SubscriberSink(ActorSubscriber[Out](sinkActor))

    val runnableFlow: RunnableFlow[In, Out] = processingSteps.withSource(publisher).withSink(sink)

    val materializedFlow: MaterializedFlow = runnableFlow.run()

    flow = Some(FlowInstance(materializedFlow, publisherActor, sinkActor))

  }

  override def preStart(): Unit = {
    super.preStart()
    createFlowFromConfig()
    switchToCustomBehavior(suspended)
  }

  private def startFlow(): Unit = {
    flow.foreach { v =>
      v.sink ! BecomeActive()
      v.source ! BecomeActive()
    }
  }

  private def stopFlow(): Unit = {
    flow.foreach { v =>
      v.source ! BecomePassive()
      v.sink ! BecomePassive()
    }
  }


  override def commonBehavior(): Receive = super.commonBehavior() orElse {
    case Acknowledged(id, msg) => msg match {
      case ProducedMessage(_, c: Cursor) =>
        for (
          func <- cursor2config;
          config <- func(c)
        ) context.parent ! TapConfigUpdate(flowId, config)
    }
  }

  private def suspended: Receive = {
    case StartFlowInstance() =>
      startFlow()
      switchToCustomBehavior(started)
  }

  private def started: Receive = {
    case SuspendFlowInstance() =>
      stopFlow()
      switchToCustomBehavior(suspended)
  }

}
