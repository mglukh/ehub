package common.actors

import akka.actor.Actor
import common.{BecomePassive, BecomeActive}


trait PipelineWithStatesActor extends ActorWithComposableBehavior {

  sealed trait RequestedState
  case class Active() extends RequestedState
  case class Passive() extends RequestedState

  private var requestedState : Option[RequestedState] = None


  def lastRequestedState = requestedState

  def becomeActive() : Unit = {}
  def becomePassive() : Unit = {}


  override def commonBehavior(): Actor.Receive = handlePipelineStateChanges orElse super.commonBehavior()

  private def handlePipelineStateChanges : Actor.Receive = {
    case BecomeActive() =>
      logger.debug("Becoming active")
      requestedState = Some(Active())
      becomeActive()
    case BecomePassive() =>
      logger.debug("Becoming passive")
      requestedState = Some(Passive())
      becomePassive()
  }

}
