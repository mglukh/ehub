package common.actors

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging

trait ActorWithComposableBehavior extends ActorUtils with StrictLogging {

  def commonBehavior() : Receive = {
    case msg : Loggable => logger.info(String.valueOf(msg))
  }

  final def switchToCustomBehavior(customBehavior: Receive, bid:Option[String] = None) = {
    logger.debug(s"Switched to custom behavior, id=$bid")
    context.become(customBehavior orElse commonBehavior)
  }

  final def switchToCommonBehavior() = {
    logger.debug("Switched to common behavior")
    context.become(commonBehavior)
  }

  override def receive: Receive = commonBehavior

}
