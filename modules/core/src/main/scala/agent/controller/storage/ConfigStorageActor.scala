package agent.controller.storage

import akka.actor.{ActorRefFactory, Actor, Props}
import com.typesafe.config.Config
import common.actors.{ActorObjWithConfig, ActorObj, ActorWithComposableBehavior}

/**
 * Created by maks on 22/09/2014.
 */
object ConfigStorageActor extends ActorObjWithConfig {
  override val id = "cfgStorage"
  override def props(implicit config: Config) = Props(new ConfigStorageActor())
}

case class TapConfig(flowId: Long, config: String, state: Option[String])

case class TapState(flowId: Long, state: Option[String])

case class StoreConfig(config: TapConfig)

case class StoreState(config: TapState)

case class RetrieveConfigFor(flowId: Long)

case class RetrieveConfigForAll()

case class StoredConfig(flowId: Long, config: Option[TapConfig])

case class StoredConfigs(configs: List[StoredConfig])

class ConfigStorageActor(implicit config: Config) extends ActorWithComposableBehavior {

  val storage = Storage(config)

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Creating DB in ${config.getString("agent.storage.directory")}, provider $storage")
  }

  override def commonBehavior(): Actor.Receive = super.commonBehavior orElse {
    case StoreConfig(TapConfig(flowId, c, s)) =>
      logger.debug(s"Persisted config and state for flow $flowId")
      storage.store(flowId, c, s)
    case StoreState(TapState(flowId, s)) =>
      logger.debug(s"Persisted state for flow $flowId")
      storage.storeState(flowId, s)
    case RetrieveConfigFor(flowId) =>
      sender() ! StoredConfig(flowId, storage.retrieve(flowId) map { case (c, s) => TapConfig(flowId, c, s)})
    case RetrieveConfigForAll() =>
      sender() ! StoredConfigs(storage.retrieveAll().map {
        case (fId, c, s) => StoredConfig(fId, Some(TapConfig(fId, c, s)))
      })
  }
}
