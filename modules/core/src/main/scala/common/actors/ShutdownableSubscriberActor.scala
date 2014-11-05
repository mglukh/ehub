package common.actors

import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError}
import common.Stop

trait ShutdownableSubscriberActor extends ActorSubscriber with ActorWithComposableBehavior {

  override def commonBehavior(): Receive = handleSubscriberShutdown orElse super.commonBehavior()

  private def stop(reason: Option[String]) = {
    logger.info(s"Shutting down subscriber, reason given: $reason")
    context.stop(self)
  }

  private def handleSubscriberShutdown : Receive = {
    case OnComplete => stop(Some("OnComplete"))
    case OnError(cause) => stop(Some("Error: " + cause.getMessage))
    case Stop(reason) => stop(reason)
  }

}
