package nugget.agent.controller.storage

import akka.actor.Props
import com.typesafe.config.Config
import nugget.core.actors.ActorWithComposableBehavior

/**
 * Created by maks on 22/09/2014.
 */
object ConfigStorageActor {
  def props(implicit config: Config) = Props(new ConfigStorageActor())
}

case class FlowConfig(flowId: Long, config: String, state: Option[String])

case class FlowState(flowId: Long, state: Option[String])

case class StoreConfig(config: FlowConfig)

case class StoreState(config: FlowState)

case class RetrieveConfigFor(flowId: Long)

case class RetrieveConfigForAll()

case class StoredConfig(flowId: Long, config: Option[FlowConfig])

case class StoredConfigs(configs: List[StoredConfig])

class ConfigStorageActor(implicit config: Config) extends ActorWithComposableBehavior {

  val storage = Storage(config)

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Creating DB in ${config.getString("agent.storage.directory")}, provider $storage")
  }

  override def commonBehavior(): Receive = super.commonBehavior orElse {
    case StoreConfig(FlowConfig(flowId, c, s)) =>
      logger.debug(s"Persisted config and state for flow $flowId")
      storage.store(flowId, c, s)
    case StoreState(FlowState(flowId, s)) =>
      logger.debug(s"Persisted state for flow $flowId")
      storage.storeState(flowId, s)
    case RetrieveConfigFor(flowId) =>
      sender() ! StoredConfig(flowId, storage.retrieve(flowId) map { case (c, s) => FlowConfig(flowId, c, s)})
    case RetrieveConfigForAll() =>
      sender() ! StoredConfigs(storage.retrieveAll().map {
        case (fId, c, s) => StoredConfig(fId, Some(FlowConfig(fId, c, s)))
      })
  }
}
