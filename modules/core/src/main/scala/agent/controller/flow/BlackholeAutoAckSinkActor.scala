package agent.controller.flow

import akka.actor.Actor.Receive
import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import common.actors.{Acknowledged, PipelineWithStatesActor, ShutdownableSubscriberActor, ActorWithComposableBehavior}

import scala.concurrent.stm.Txn.Active

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


  override def commonBehavior(): Actor.Receive = super.commonBehavior() orElse {
    case OnNext(msg) => context.parent ! Acknowledged[Any](-1, msg)
  }

}