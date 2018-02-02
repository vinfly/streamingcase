package sesiion

/**
  * Created by mac on 17/10/17.
  */

import java.text.SimpleDateFormat
import java.util.Date

object DateUtils {
  /**
    * 日期时间工具类
    */
  val TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  val DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")
  val DATEKEY_FORMAT = new SimpleDateFormat("yyyyMMdd")

  /**
    * 判断一个时间是否在另一个时间之前
    * @param time1
    * @param time2
    * @return
    */
  def before(time1: String, time2: String): Boolean = {
    val dateTime1 = TIME_FORMAT.parse(time1)
    val dateTime2 = TIME_FORMAT.parse(time2)
    if (dateTime1.before(dateTime2)) true else false

  }

  /**
    * 判断一个时间是否在另一个时间之后
    * @param time1
    * @param time2
    * @return
    */
  def after(time1: String, time2: String): Boolean = {
    val dateTime1 = TIME_FORMAT.parse(time1)
    val dateTime2 = TIME_FORMAT.parse(time2)
    if (dateTime1.after(dateTime2)) true else false

  }

  def minus(time1: String, time2: String): Int = {
    val dateTime1 = TIME_FORMAT.parse(time1)
    val dateTime2 = TIME_FORMAT.parse(time2)
    val millisecond: Long = dateTime1.getTime - dateTime2.getTime
    (millisecond / 1000).toString.toInt
  }

  def getTodayDate(): String = {
    try {
      DATE_FORMAT.format(new Date)
    } catch {
      case e: Exception => null
    }
  }

  def formatTime(date: Date): String = {
    try {
      TIME_FORMAT.format(date)
    } catch {
      case e: Exception => null
    }

  }

  def parseTime(time: String): Date = {
    try {
      TIME_FORMAT.parse(time)
    } catch {
      case e: Exception => null
    }

  }

  def getDateHour(datetime: String): String = {
    var value:String = ""
    try {
      val date = datetime.split(" ")(0)
      val hourMinuteSecond = datetime.split(" ")(1)
      val hour = hourMinuteSecond.split(":")(0)
      value = date + "_" + hour
    } catch {
      case e: Exception => value = ""
    }
    value

  }

}
