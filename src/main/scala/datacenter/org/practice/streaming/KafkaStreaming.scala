package datacenter.org.practice.streaming

import kafka.serializer.StringDecoder
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka.KafkaUtils

/**
  * Created by mac on 17/11/10.
  */
object KafkaStreaming {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setAppName("KafKaStreming")
      .setMaster("local[2]")

    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(1))

    ssc.checkpoint("/tmp/sparkstreaming/kafka/")

    val kafkaParams = Map("metadata.broker.list" -> "10.22.253.227:9092")

    val topics = Set("orderTopic")

    val socketDStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](
      ssc,
      kafkaParams,
      topics
    ).map(_._2)

    val words = socketDStream.flatMap(_.split(" "))

    val pairs = words.map(word => (word, 1))

    val wordCounts = pairs.updateStateByKey(
      (values: Seq[Int], state: Option[Int]) => {
        val currentCount = values.sum
        val previosCount = state.getOrElse(0)
        Some(currentCount + previosCount)
      }
    )
    wordCounts.print()

    ssc.start()             // Start the computation
    ssc.awaitTermination()  // Wait for the computation to terminate

    ssc.sparkContext.stop()

  }

}