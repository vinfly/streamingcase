package datacenter.org.practice.realstreaming


import java.text.{NumberFormat, SimpleDateFormat}
import java.util.Date

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import kafka.serializer.StringDecoder
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Duration, Minutes, Seconds, StreamingContext}
import redis.clients.jedis.JedisPool


/**
  * Created by mac on 17/12/22.
  */


/**
  * 从kafka topic中获取数据,统计订单量和订单总值
  */
object OrderTurnoverStreaming {

  val BATCH_INTERVAL: Duration = Seconds(1)
  val WINDOW_INTERVAL: Duration = BATCH_INTERVAL * 5
  val SLIDER_INTERVAL: Duration = BATCH_INTERVAL * 3

  val CHECKPOINT_DIRCTORY: String = "/user/248404/realstreaming/kafka/"


  //贷出模式（Loan Pettern）

  def sparkOperation(args: Array[String])(operation: StreamingContext => Unit): Unit = {

    /**
      * Function to create and setup a new StreamingContext
      */
    def functionToCreateContext(): StreamingContext = {
      //读取Spark Application配置信息
      val sparkConf = new SparkConf()
        .setAppName("OrderTurnoverStreaming Application")
        .setMaster("local[3]")

      sparkConf
        .set("spark.eventLog.enabled", "true")
        .set("park.eventLog.dir", "hdfs://dc226.dooioo.cn:8020/user/248404/realstreaming/spark/eventLogs")
        .set("spark.eventLog.compress", "false")

      // TODO: 设置 从Kafka Topic中每秒中获取每个分区数据对多的条目数
      sparkConf.set("spark.streaming.kafka.maxRatePerPartitio", "10000")

      // TODO: 设置序列化方式
      sparkConf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      sparkConf.registerKryoClasses(Array(classOf[Order]))

      // TODO: 设置Executor JVM的GC机制, 由于使用的JDK8, 推荐使用G1
      // sparkConf.set("spark.executor.extraJavaOptions", "-XX:+UseG1GC")
      // Spark 1.x中的设置，使用JDK 7
      sparkConf.set("spark.executor.extraJavaOptions", "-XX:+UseConcMarkSweepGC")

      // TODO: SparkStreaming与SparkSQL集成时参数调优
      sparkConf.set("spark.sql.shuffle.partitions", "6")
      // Spark 1.x中设置，需要下面属性配置
      sparkConf.set("spark.sql.tungsten.enabled", "false")
      // sparkConf.set("spark.streaming.stopGracefullyOnShutdown","true")

      // 创建SparkContext上下文对象
      val sc = SparkContext.getOrCreate(sparkConf)
      // 设置日志级别
      sc.setLogLevel("WARN")

      /**
        * 创建StreamingContext对象实例，设置 batch interval为 2s
        */
      val ssc = new StreamingContext(sc, BATCH_INTERVAL)

      // 调用用户函数，处理数据: input -> process -> output
      operation(ssc)

      // To make sure data is not deleted by the time we query it interactively
      ssc.remember(Minutes(1))
      // 设置检查点目录
      ssc.checkpoint(CHECKPOINT_DIRCTORY)

      // return StreamingContext
      ssc
    }

    // 创建StreamingContext
    var context: StreamingContext = null

    try {
      // Stop any existing StreamingContext
      val stopActiveContext = true
      if (stopActiveContext) {
        StreamingContext.getActive().foreach(_.stop(stopSparkContext = true))
      }
      // 创建StreamingContext, 如果是第一次的话调用functionToCreateContext创建，否则从检查点目录创建
      // context = StreamingContext.getOrCreate(CHECKPOINT_DIRCTORY, functionToCreateContext)
      context = StreamingContext.getActiveOrCreate(CHECKPOINT_DIRCTORY, functionToCreateContext)

      //如果从检查点恢复创建StreamingContext,日志级别的设置不生效,重新设置
      context.sparkContext.setLogLevel("WARN")
      context.start()
      context.awaitTermination()
    } catch {
      case e: Exception => e.printStackTrace()
    }
    //    finally {
    //      // 当应用 终止以后，需要管理程序执行
    //      context.stop(stopSparkContext = true, stopGracefully = true)
    //    }
  }

  def main(args: Array[String]): Unit = {
    sparkOperation(args)(processOrderData)

  }

  /**
    * 贷出模式 称为  用户函数： 真正编写 程序逻辑的地方
    *
    * @param ssc
    */
  def processOrderData(ssc: StreamingContext): Unit = {

    /** ===================1. 从Kafka Topic中读取数据,采用Direct方式============== */
    //设置kafka 连接相关信息
    val kafkaParams: Map[String, String] = Map(
      "metadata.broker.list" -> "10.22.253.230:9092,10.22.253.227:9092,10.22.253.228:9092,10.22.253.229:9092",
      "auto.offset.reset" -> "largest"
    )
    val topicsSet: Set[String] = "datacenter".split(",").toSet

    //采用直接取的方式从kafka Topic读取数据
    val orderDStream: InputDStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topicsSet)

    /** ====================2. 处理订单数据:实时累加统计各省份营业额 ==================*/

    //    val orderTurnoverDStream: DStream[(Int, Float)] =orderDStream
    //      .transform(rdd => {
    //      //解析JSON格式的数据
    //      val orderRDD: RDD[Order] = rdd.map(tuple => {
    ////        ObjectMapperSingleton.getInstance().readValue(tuple._2, classOf[Order])
    //        val instance = new ObjectMapper()
    //          instance.registerModule(DefaultScalaModule)
    //        instance.readValue(tuple._2,classOf[Order])
    //      })
    //      orderRDD.map(order=>{
    //        (order.provinceId,order)
    //      })
    //    })
    //
    val orderTurnoverDStream: DStream[(Int, Float)] = orderDStream
      // a. 解析JSON格式的数据，数据转换为DStream[(Key, Value)]
      .map(tuple => {
      // 解析JSON格式数据为Order
      val instance = new ObjectMapper()
      instance.registerModule(DefaultScalaModule)
      val order: Order = instance.readValue(tuple._2, classOf[Order])
      (order.provinceId, order)
    })
      //b.使用updateStateByKey进行实时累加统计 - 各省份
      .updateStateByKey(
      (orders: Seq[Order], state: Option[Float]) => {

        //获取省份当前传递进来的订单营业额
        val currentOrderPrice: Float = orders.map(_.price).sum

        //获取当前省份以前订单营业额
        val previousPrice = state.getOrElse(0.0F)

        //累加订单营业额,返回
        Some(currentOrderPrice + previousPrice)
      }
    )
    /** ==================3. 将各个省份实时统计的营业额进行输出================== */

    orderTurnoverDStream
      .foreachRDD(
        (rdd, time) => {
          val batchInterval = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new Date(time.milliseconds))

          println("----------------------------------------------")
          println(s"batchInterval : ${batchInterval}")
          println("----------------------------------------------")
          rdd.coalesce(1)
            .foreachPartition(iter => {
              iter.foreach(item => println(item._1 + "--->" + NumberFormat.getInstance().format(item._2)))
            })

          /** 写入Redis中 */

              rdd.coalesce(1)
                .foreachPartition(iter => {
                  val poolConfig: GenericObjectPoolConfig = new GenericObjectPoolConfig()
                  val jedisPool = new JedisPool(poolConfig, "10.22.253.227", 6379)
                  val jedis = jedisPool.getResource
                  iter.foreach(item => {
                    jedis.hset(
                      "order:turnover:province",
                      item._1.toString,
                      NumberFormat.getInstance().format(item._2)
                    )
                  })
            })

        }
      )


  }

}

///**
//  * 常见 ObjectMapper 单例对象
//  */
//object ObjectMapperSingleton {
//  /**
//    * 声明对象是 使用 transient 有如下含义:
//    * - 当对象被序列化的时候，transient 阻止实例中那些用此关键字声明的变量持久化
//    * - 当对象反序列化的时候，实例变量不会被持久化和恢复
//    */
//  @transient private var instance: ObjectMapper = null
//
//  def getInstance(): ObjectMapper = {
//    if (instance == null) {
//      instance == new ObjectMapper()
//      instance.registerModule(DefaultScalaModule)
//    }
//    instance
//  }
//}
