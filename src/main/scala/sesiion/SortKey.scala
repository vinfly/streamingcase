package sesiion

/**
  * Created by mac on 17/10/19.
  */
class SortKey(val clickCount: Long, val orderCount: Long, val payCount: Long) extends Ordered[SortKey] with Serializable {
  def compare(that: SortKey): Int = {
    if (clickCount - that.clickCount != 0) {
      (clickCount - that.clickCount).toInt
    } else if (orderCount - that.orderCount != 0) {
      (orderCount - that.orderCount).toInt
    } else if (payCount - that.payCount != 0) {
      (payCount - that.payCount).toInt
    } else {
      0
    }
  }

}
