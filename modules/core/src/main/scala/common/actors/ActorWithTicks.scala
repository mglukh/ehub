package common.actors

import scala.concurrent.duration.DurationLong

trait ActorWithTicks extends ActorWithComposableBehavior {

  implicit private val ec = context.dispatcher

  def tickInterval = 1.second

  override def preStart(): Unit = {
    scheduleTick()
    super.preStart()
  }

  private def scheduleTick() = this.context.system.scheduler.scheduleOnce(tickInterval, self, Tick())(context.dispatcher)

  def processTick() : Unit


  override def commonBehavior(): Receive = handleTicks orElse super.commonBehavior()

  private def handleTicks : Receive = {
    case Tick() =>
      processTick()
      scheduleTick()
  }

  private case class Tick()


}
