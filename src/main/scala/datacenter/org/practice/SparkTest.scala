package datacenter.org.practice

import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by mac on 17/10/20.
  */
object SparkTest {

  def main(args: Array[String]): Unit = {

    //创建 spark 上下文环境

    val conf =new SparkConf()
      .setAppName("datacenter")
      .setMaster("local[2]")

    val sc =new SparkContext(conf)


    val hiveContext = new HiveContext(sc)


    val df = hiveContext.sql("select * from test.user_visit_format")
    df.show()

    sc.stop()











  }

}
