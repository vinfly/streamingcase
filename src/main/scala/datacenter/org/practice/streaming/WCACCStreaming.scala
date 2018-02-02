package datacenter.org.practice.streaming

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * Created by mac on 17/11/10.
  */
object WCACCStreaming {


  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setMaster("local[2]")
      .setAppName("UnUpdateWordCount")

    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(5))
    ssc.checkpoint("/tmp/sparkstreaming/socketwc/")

    val socketDStream = ssc.socketTextStream("10.22.253.227", 9999)

    val filterRdd = sc.parallelize(List(".", "?", "@", "#", "$", "!", ",", "=", "*", "%")).map((_, true))

    val filteredDStream = socketDStream.transform(rdd => {

      val tupleRdd = rdd.flatMap(_.split(" ")).map((_, 1))

      tupleRdd.leftOuterJoin(filterRdd).filter(tuple => {
        val x1 = tuple._1
        val x2 = tuple._2
        x2._2.isEmpty
      }).map(tuple => {
        (tuple._1, 1)
      })

    })


    val WordConutsDStream = filteredDStream.updateStateByKey(

      (values: Seq[Int], state: Option[Int]) => {

        val currentCount = values.sum

        val previousCount = state.getOrElse(0) //if mull 0 else value

        Some(currentCount + previousCount)

      }

    )
    WordConutsDStream.print()

    ssc.start()
    ssc.awaitTermination()
    sc.stop()


  }
}
