package agent.flavors.files

trait Cursor {
}

case class IndexedEntity(idx: ResourceIndex, id: FileResourceIdentificator)

case class ResourceIndex(seed: Long, resourceId: Long)

case class FileCursor(idx: ResourceIndex, positionWithinItem: Long) extends Cursor
case class NilCursor() extends Cursor

case class FileResourceIdentificator(dir: String, name: String, createdTimestamp: Long, sizeNow: Long)  {
  def same(that: FileResourceIdentificator): Boolean = that match {
    case FileResourceIdentificator(thatDir, thatName, thatCreatedTs, thatSize) =>
      thatDir == dir && thatName == name && thatCreatedTs == createdTimestamp && thatSize >= sizeNow
    case _ => false
  }
}
