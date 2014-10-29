package pyrio.agent

import java.nio.ByteBuffer

trait Cursor extends WireData {
}
case class RollingFileCursor(itemId: Long, positionWithinItem: Long) extends Cursor {
  override def serialize: Array[Byte] = {
    val bb = ByteBuffer.allocate(16).putLong(itemId).putLong(positionWithinItem)
    bb.flip()
    bb.array()
  }

  override def deserialize(data: Array[Byte]): RollingFileCursor = {
    val wrap: ByteBuffer = ByteBuffer.wrap(data)
    RollingFileCursor(wrap.getLong, wrap.getLong)
  }

  def +(positionDiff: Long): RollingFileCursor = {
    RollingFileCursor(itemId, positionWithinItem + positionDiff)
  }

  def diff(that: RollingFileCursor): Option[Long] = {
    if (that.itemId == itemId) {
      val diff = that.positionWithinItem - positionWithinItem
      if (diff >= 0) {
        return Some(diff)
      }
    }
    None
  }


}

