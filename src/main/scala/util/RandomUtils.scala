package util

import java.util.Random

object RandomUtils {

  def getRandomNum(bound :Int):Int={

    val random = new Random()
    random.nextInt(bound)
  }
}
