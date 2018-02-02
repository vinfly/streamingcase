package datacenter.org.practice.realstreaming

import java.util.{Properties, UUID}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import util.RandomUtils

import scala.collection.mutable.ArrayBuffer


case class Order(orderId: String, provinceId: Int, price: Float)


object OrderProducer {

  def main(args: Array[String]): Unit = {

    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)


    val props = new Properties()
    props.put("metadata.broker.list", "10.22.253.227:9092")
    props.put("serializer.class", "kafka.serializer.StringEncoder")
    props.put("key.serializer.class", "kafka.serializer.StringEncoder")
    //向Topic中存储数据采用的方式,默认值为sync同步方式,设置为异步方式存储数据,数据存储较多
    props.put("producer.type", "async")


    var producer: Producer[String, String] = null

    val config: ProducerConfig = new ProducerConfig(props)

    producer = new Producer[String, String](config)

    val messageArrayBuffer = new ArrayBuffer[KeyedMessage[String, String]]()

    while (true) {

      messageArrayBuffer.clear()

      val random: Int = RandomUtils.getRandomNum(1000) + 12000

      for (index <- 0 until random) {
        val orderId = UUID.randomUUID().toString
        val provincedId = RandomUtils.getRandomNum(35)
        val orderPrice = RandomUtils.getRandomNum(100) + 100.5F

        val order = Order(orderId, provincedId, orderPrice)

        val message: KeyedMessage[String, String] = new KeyedMessage[String, String]("datacenter", orderId, mapper.writeValueAsString(order))

        messageArrayBuffer += message
      }
      producer.send(messageArrayBuffer: _*)

    }


  }

}
