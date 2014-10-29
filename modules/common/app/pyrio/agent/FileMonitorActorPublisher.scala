package pyrio.agent

import java.io.{FileInputStream, InputStreamReader, BufferedReader, File}
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.zip.{ZipInputStream, GZIPInputStream}

import akka.stream.actor.{ActorPublisherMessage, ActorPublisher}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationDouble


case class BoundedChunk[T, C <: Cursor](data: Option[T], cursor: C)


trait NonBlockingResourcePullingProxy[T, C <: Cursor] {

  def pullRetryInterval = 3.second

  def next(c: Option[C]): Option[BoundedChunk[T, C]]

  def cancelResource()
}


case class ProducedMessage[T, C <: Cursor](bs: T, c: C)

class NonBlockingPullingActorPublisher[T, C <: Cursor](val proxy: NonBlockingResourcePullingProxy[T, C], val initialCursor: Option[C])
                                                      (implicit ec: ExecutionContext)
  extends ActorPublisher[ProducedMessage[T, C]] with LazyLogging {

  private var currentCursor = initialCursor

  override def preStart() = {
    scheduleTick()
  }

  private def scheduleTick(): Unit = context.system.scheduler.scheduleOnce(proxy.pullRetryInterval, self, Tick)

  override def receive: Receive = {
    case Tick =>
      pullAndReleaseNext()
      scheduleTick()
    case ActorPublisherMessage.Request(n) =>
      logger.debug(s"Downstream requested $n entries")
      pullAndReleaseNext()
    case ActorPublisherMessage.Cancel =>
      logger.debug(s"Downstream cancels the stream")
      proxy.cancelResource()
    case u =>
      logger.debug(s"Received unknown message $u")
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
            pullAndReleaseNext()
          }
        case None =>
          logger.info(s"No data available")
      }
    }
  }

  private case class Tick()

}


case class OpenResource(itemId: Long, pathAndFileName: String)(implicit charset: Charset) extends LazyLogging {

  private val file = new File(pathAndFileName)
  private val stream = openStream(file)
  private val reader = new BufferedReader(new InputStreamReader(stream, charset))
  private var lastSeenSize = file.length()
  private var cursor = RollingFileCursor(itemId, 0);

  private var ab = CharBuffer.allocate(1024 * 64).array()

  def currentCursor = cursor

  def read(): Option[ByteString] = {
    reader.read(ab) match {
      case i if i < 1 => None
      case i =>
        cursor = cursor + i
        Some(ByteString(new String(ab, 0, i)))
    }
  }

  def sameResource(s: String): Boolean = {
    new File(s).equals(file)
  }

  def toCursor(newCursor: RollingFileCursor): Option[OpenResource] = {
    (cursor diff newCursor).map(skip)
  }

  def skip(n: Long): OpenResource = {
    if (n > 0) {
      val skipped = reader.skip(n)
      cursor = cursor + skipped
      logger.debug(s"Tried to skip $n entries, skipped $skipped, new cursor $cursor")
    }
    this
  }

  def skipToTail(): OpenResource = {
    logger.debug("Skipping to tail")
    skip(Long.MaxValue)
    this
  }

  def wasTruncated_? = {
    val newLength = file.length()
    val truncated = newLength < lastSeenSize
    logger.debug(s"was truncated: $truncated  (last seen size: $lastSeenSize, new size: $newLength)")
    lastSeenSize = newLength
    truncated
  }

  def close(): Unit = {
    logger.debug("Closing resource")
    reader.close()
  }

  private def sameEntity(cursor: RollingFileCursor): Boolean = {
    cursor.itemId == itemId
  }

  private def openStream(file: File) = {
    logger.debug(s"Opening stream from $file")
    val rawStream = new FileInputStream(file)
    if (file.getName.endsWith("gz")) {
      new GZIPInputStream(rawStream)
    } else if (file.getName.endsWith("zip")) {
      new ZipInputStream(rawStream)
    } else {
      rawStream
    }
  }
}

class NonBlockingFileMonitorPullingProxy(target: MonitorTarget)(implicit fileIndexing: FileIndexing, charset: Charset)
  extends NonBlockingResourcePullingProxy[ByteString, RollingFileCursor] with LazyLogging {

  val fileIndexingSession = fileIndexing.startSession(target)
  var cancelled = false

  var resource: Option[OpenResource] = None


  def progressToNextItem(): Option[RollingFileCursor] = {
    (for (
      or <- resource if fileIndexingSession.getAbsoluteFileLocationAt(or.itemId).exists(or.sameResource);
      nextId <- fileIndexingSession.locateNextItem(or.itemId);
      newResource <- resourceAt(RollingFileCursor(nextId, 0))
    ) yield newResource) match {
      case Some(res) =>
        logger.debug(s"Progressed, new resource - $res")
        Some(res.currentCursor)
      case None =>
        closeOpenResource()
        None
    }
  }

  override def next(c: Option[RollingFileCursor]): Option[BoundedChunk[ByteString, RollingFileCursor]] = {
    if (cancelled) return None
    (c orElse initialCursor) flatMap { cur =>
      resourceAt(cur) flatMap { resource =>
        val chunk = resource.read()
        val currentCursor = resource.currentCursor
        chunk.map(v => BoundedChunk(Some(v), currentCursor)) orElse
          Some(BoundedChunk(None, progressToNextItem() getOrElse currentCursor))
      }
    }
  }

  override def cancelResource(): Unit = {
    logger.debug("cancelResource()")
    cancelled = true
    closeOpenResource()
    fileIndexingSession.close()
  }

  private def closeOpenResource() = {
    resource.foreach(_.close())
    resource = None
  }

  private def initialCursor: Option[RollingFileCursor] = {
    for (
      idx <- fileIndexingSession.tailIndex();
      resource <- resourceAt(RollingFileCursor(idx, 0))
    ) yield resource.skipToTail().currentCursor
  }

  private def fileExists(cursor: RollingFileCursor) = {
    fileIndexingSession.getAbsoluteFileLocationAt(cursor.itemId).map(new File(_)).exists(f => f.exists() && f.isFile)
  }

  private def openResource(cursor: RollingFileCursor, file: String) = {
    closeOpenResource()
    val resourceRef = OpenResource(cursor.itemId, file).skip(cursor.positionWithinItem)
    resource = Some(resourceRef)
    logger.debug(s"Opened $file (id ${cursor.itemId}) - $resourceRef")
    resourceRef
  }

  private def resourceAt(cursor: RollingFileCursor): Option[OpenResource] = {
    (fileIndexingSession.getAbsoluteFileLocationAt(cursor.itemId) orElse
      fileIndexingSession.tailIndex().flatMap(fileIndexingSession.getAbsoluteFileLocationAt)) map { filePathAndName =>
      resource match {
        case None => openResource(cursor, filePathAndName)
        case Some(r) if !r.sameResource(filePathAndName) => openResource(cursor, filePathAndName)
        case Some(r) if r.wasTruncated_? => {
          openResource(RollingFileCursor(cursor.itemId, 0), filePathAndName)
        }
        case Some(r) => r.toCursor(cursor) match {
          case Some(movedResource) => movedResource
          case None => openResource(cursor, filePathAndName)
        }
      }
    }
  }


}

object FileMonitorActorPublisher {

  def apply(target: MonitorTarget, cursor: Option[RollingFileCursor] = None)
           (implicit fileIndexing: FileIndexing, charset: Charset, ec: ExecutionContext) = {
    new NonBlockingPullingActorPublisher[ByteString, RollingFileCursor](new NonBlockingFileMonitorPullingProxy(target), cursor)
  }
}




