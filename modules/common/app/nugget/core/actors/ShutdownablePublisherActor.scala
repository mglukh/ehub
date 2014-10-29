package nugget.core.actors

import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import nugget.core.Stop

trait ShutdownablePublisherActor[T] extends ActorPublisher[T] with ActorWithComposableBehavior {

  override def commonBehavior(): Receive = handlePublisherShutdown orElse super.commonBehavior()

  private def stop(reason: Option[String]) = {
    logger.info(s"Shutting down publisher, reason given: $reason")
    context.stop(self)
  }

  private def handlePublisherShutdown : Receive = {
    case Cancel => stop(Some("Cancelled"))
    case Stop(reason) => stop(reason)
  }

}
