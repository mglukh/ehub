package nugget.core

trait NowProvider {

  def now = System.currentTimeMillis()

}
