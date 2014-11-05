package nugget.agent.controller.flow

import akka.actor.Props
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import nugget.core.actors.{Acknowledged, ActorWithComposableBehavior, PipelineWithStatesActor, ShutdownableSubscriberActor}

/**
 * Created by maks on 21/09/2014.
 */
object BlackholeAutoAckSinkActor {
  def props = Props(new BlackholeAutoAckSinkActor())
}

class BlackholeAutoAckSinkActor
  extends ActorWithComposableBehavior
  with ShutdownableSubscriberActor with PipelineWithStatesActor {


  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }


  override def commonBehavior(): Receive = super.commonBehavior() orElse {
    case OnNext(msg) => context.parent ! Acknowledged[Any](-1, msg)
  }

}