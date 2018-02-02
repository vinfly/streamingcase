package datacenter.org.practice.streaming

import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by mac on 17/11/10.
  */
object WCStreaming {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setAppName("WordCount")
      .setMaster("local[2]")

    val sc = new SparkContext(conf)

    val ssc = new StreamingContext(sc, Seconds(5))

    val dsStream = ssc.socketTextStream("10.22.253.227", 9999)

    val words = dsStream.flatMap(_.split(" "))

    val pairs = words.map((_, 1))
    val wordsCount = pairs.reduceByKey(_ + _)

    wordsCount.print()


    ssc.start()
    ssc.awaitTermination()
    sc.stop()


  }


}
