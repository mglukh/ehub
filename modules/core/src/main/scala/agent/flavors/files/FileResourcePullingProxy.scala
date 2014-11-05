package agent.flavors.files

import java.nio.charset.Charset

import akka.actor.Props
import akka.stream.actor.ActorPublisherMessage
import akka.util.ByteString
import nugget.core.actors._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

class FileResourcePullingProxy(flowId: Long, target: MonitorTarget)(implicit indexer: Indexer) extends ResourcePullingProxy[ByteString, Cursor]{

  var cancelled = false

  val indexerSession = indexer.startSession(flowId, target)


  private def initialCursor : Cursor = {
    indexerSession.tailCursor
  }

  override def next(c: Option[Cursor]): Option[DataChunk[ByteString, Cursor]] = {
    if (cancelled) return None
    val cursor = c getOrElse initialCursor
    indexerSession.withOpenResource[ByteString](cursor) { resource =>
      resource.nextChunk()
    }
  }

  override def cancelResource(): Unit = {
    cancelled = true
    indexerSession.close()
  }

}


case class ProducedMessage[T, C <: Cursor](bs: T, c: C)

class PullingActorPublisher[T, C <: Cursor](val proxy: ResourcePullingProxy[T, C], val initialCursor: Option[C])
                                                      (implicit ec: ExecutionContext)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithTicks
  with ShutdownablePublisherActor[ProducedMessage[T, C]] {

  private var currentCursor = initialCursor


  override def processTick(): Unit = lastRequestedState match {
    case Some(Active()) => pullAndReleaseNext()
    case _ => ()
  }



  override def becomeActive(): Unit = {
    logger.info("Publisher becoming active")
    switchToCustomBehavior(commonBehavior orElse handlePublisherMessages, Some("active"))
  }

  override def becomePassive(): Unit = {
    logger.info("Publisher becoming passive")
    switchToCommonBehavior()
  }

  private def handlePublisherMessages: Receive = {
    case ActorPublisherMessage.Request(n) =>
      logger.debug(s"Downstream requested $n entries")
      pullAndReleaseNext()
    case ActorPublisherMessage.Cancel =>
      logger.debug(s"Downstream cancels the stream")
      proxy.cancelResource()
  }

  @tailrec
  private def pullAndReleaseNext(): Unit = {
    if (totalDemand > 0 && isActive) {
      val entry = proxy.next(currentCursor)

      entry match {
        case Some(e) =>

          currentCursor = Some(e.cursor)

          if (e.data.isDefined) {
            onNext(ProducedMessage(e.data.get, e.cursor))
            logger.info(s"Published next entry, current cursor: $currentCursor")

            if (e.hasMore)
              pullAndReleaseNext()
            else
              logger.debug(s"Reached tail")
          }
        case None =>
          logger.info(s"No data available")
      }
    }
  }


}

object FileMonitorActorPublisher {

  def props(flowId: Long, target: MonitorTarget, cursor: Option[Cursor] = None)
           (implicit fileIndexing: Indexer, charset: Charset, ec: ExecutionContext) = Props(
    new PullingActorPublisher[ByteString, Cursor](new FileResourcePullingProxy(flowId, target), cursor))

}



