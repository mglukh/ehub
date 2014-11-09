package agent.controller

import agent.controller.flow.{FlowActor, FlowConfigUpdate, StartFlowInstance, SuspendFlowInstance}
import agent.controller.storage._
import agent.shared._
import akka.actor.{ActorRef, Props}
import akka.stream.scaladsl2.FlowMaterializer
import com.typesafe.config.Config
import common.actors.{ActorWithComposableBehavior, ReconnectingActor}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

/**
 * Created by maks on 18/09/14.
 */
object AgentControllerActor {
  def props(implicit config: Config) = Props(new AgentControllerActor())
}

class AgentControllerActor(implicit config: Config)
  extends ActorWithComposableBehavior
  with ReconnectingActor {

  implicit val mat = FlowMaterializer()
  implicit val system = context.system
  val flowActors: mutable.Map[Long, ActorRef] = mutable.HashMap()
  var storage: ActorRef = context.actorOf(ConfigStorageActor.props, "storage")
  var commProxy: Option[ActorRef] = None

  override def commonBehavior(): Receive = commonMessageHandler orElse super.commonBehavior()

  private def commonMessageHandler: Receive = handleReconnectMessages orElse {
    case ConnectedState() =>
      remoteActorRef.foreach(_ ! Handshake(config.as[String]("agent.name")))
    case DisconnectedState() =>
      commProxy = None
      initiateReconnect()
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQ(info)
      sendToHQ(snapshot)
  }

  private def sendToHQ(json: JsValue) = {
    commProxy foreach { actor =>
      logger.debug(s"$json -> $actor")
      actor ! GenericJSONMessage(json.toString())
    }
  }

  private def snapshot = Json.obj()

  private def info = Json.obj(
    "info" -> Json.obj(
      "name" -> config.as[String]("agent.name"),
      "text" -> ("some random text from " + config.as[String]("agent.name")),
      "state" -> "active"
    )
  )


  override def connectionEndpoint: String = config.as[String]("agent.hq.endpoint")

  override def preStart(): Unit = {
    initiateReconnect()
    storage ! RetrieveConfigForAll()
    super.preStart()
    switchToCustomBehavior(handleInitialisationMessages, Some("awaiting initialisation"))
  }

  private def handleInitialisationMessages: Receive = {
    case StoredConfigs(list) =>
      logger.info("!>>> " + list)
      list.foreach {
        case StoredConfig(id, Some(FlowConfig(_, cfg, state))) =>
          createActor(id, cfg, state)
        case StoredConfig(id, None) =>
          logger.warn(s"No config defined for flow ID $id")
      }
      switchToCustomBehavior(handleFlowOpMessages, Some("with flows inititalised"))
  }

  private def handleFlowOpMessages: Receive = {

    case CreateFlow(cfg) =>
      val flowId = (cfg \ "flowId").as[Long]
      val cfgAsStr: String = Json.stringify(cfg)
      logger.info("Creating flow " + flowId)
      storage ! StoreConfig(FlowConfig(flowId, cfgAsStr, None))
      createActor(flowId, cfgAsStr, None)
    case StartFlow(id) =>
      logger.info("Starting flow " + id)
      flowActors.get(id).foreach(_ ! StartFlowInstance())
    case StopFlow(id) =>
      logger.info("Stopping flow " + id)
      flowActors.get(id).foreach(_ ! SuspendFlowInstance())
    case FlowConfigUpdate(id, state) =>
      logger.info(s"Flow state update: id=$id state=$state")
      storage ! StoreState(FlowState(id, Some(Json.stringify(state))))
  }

  def createActor(flowId: Long, config: String, maybeState: Option[String]): Unit = {
    flowActors.get(flowId).foreach { actor =>
      logger.info(s"Stopping $actor")
      context.stop(actor)
    }
    logger.info(s"Creating a new actor for flow $flowId")
    flowActors += (flowId -> context.actorOf(FlowActor.props(flowId, Json.parse(config), maybeState.map(Json.parse)), "flow" + flowId))
  }

  //  private case class CreateFlowWith(flowId: Long, config: JsValue, state: Option[JsValue])

}