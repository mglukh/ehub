package nugget.core.actors

import nugget.core.{BecomeActive, BecomePassive}



trait PipelineWithStatesActor extends ActorWithComposableBehavior {

  sealed trait RequestedState
  case class Active() extends RequestedState
  case class Passive() extends RequestedState

  private var requestedState : Option[RequestedState] = None


  def lastRequestedState = requestedState

  def becomeActive() : Unit = {}
  def becomePassive() : Unit = {}


  override def commonBehavior(): Receive = handlePipelineStateChanges orElse super.commonBehavior()

  private def handlePipelineStateChanges : Receive = {
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
