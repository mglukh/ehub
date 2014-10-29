package pyrio.agent

trait WireData {
  def serialize: Array[Byte]

  def deserialize(data: Array[Byte]) : WireData
}
