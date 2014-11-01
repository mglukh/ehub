package hq.gates

import akka.actor.Props
import nugget.core.actors.PipelineWithStatesActor

object GateActor {
  def props = Props(new GateActor())
}
class GateActor extends PipelineWithStatesActor {

  override def becomeActive(): Unit = {}
  override def becomePassive(): Unit = {}

}