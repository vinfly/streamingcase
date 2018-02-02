package sesiion

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.hive.HiveContext

import scala.collection.mutable.ArrayBuffer

/**
  * Created by mac on 17/10/20.
  */
object UserVisitAnalysis {

  def main(args: Array[String]): Unit = {

    val conf =new SparkConf()
      .setAppName("datacenter")
      .setMaster("local[2]")

    val sc =new SparkContext(conf)


    val hiveContext = new HiveContext(sc)

    val actionRDD = hiveContext.sql("select * from test.user_visit_format where date>='2017-10-15' and date<='2017-10-18'").rdd

    val filteredRDD = filteredBySession(hiveContext,actionRDD)


    filteredRDD.take(10).foreach(println)

    println("=====================Top 10 ==========================")



    val session2detailRDD = filteredRDD.map(row => {
      val sessionid = row.getString(2)
      (sessionid, row)
    })

    val top10CategoryList = getTop10Category(session2detailRDD)




    for (e <- top10CategoryList) {
      println(e._2)
    }
//    (key1 value1)
//
//    (key1 value)
//    (key1,(value1,value2))


sc.stop()







  }

  def filteredBySession(hiveContext:HiveContext,actionRDD:RDD[Row]):RDD[Row]={

    val userid2ActionRDD = actionRDD.map(row=>{(row.getLong(1),row)})


    val userInfoRDD = hiveContext.sql("select * from test.user_info").rdd

    val userid2InfoRD = userInfoRDD.map(row=>{
      val userid = row.getLong(0)
      (userid,row)
    }
    )

    val useid2FullInfo = userid2ActionRDD.join(userid2InfoRD)


    useid2FullInfo.filter(tuple=>{

     // val userid=tuple._1
      val userInfo =tuple._2._2
      val age = userInfo.getInt(3)

      age>=20 && age<=40
      }).map(_._2._1)





  }
  /**
    * 获取Top10热门品类
    */

//
//  (sessionid, <ROw>)



  private def getTop10Category(session2detailRDD: RDD[(String, Row)]): Array[(SortKey, String)] = {
    /*
    第一步,获取符合条件的session访问过的所有品类
     */
    //获取符合条件的session的访问明细

    //获取session访问过的所有品类id,及点击过,下单过,支付过
    val categoryidRDD = session2detailRDD.flatMap(tuple => {
      val row: Row = tuple._2
      val list = ArrayBuffer[(Long, Long)]()

      var clickCategoryId: Long = 0L
      try {
        clickCategoryId = row.getLong(6)
      } catch {
        case e: Exception => clickCategoryId = 0L
      }
      if (clickCategoryId != 0L) {
        list += ((clickCategoryId, clickCategoryId))
      }


      val orderCategoryIds: Option[String] = Option(row.getString(8))
      orderCategoryIds match {
        case Some(str) => {
          if (!str.equals("")) {
            val orderCategoryIdsSplited: Array[String] = str.split(",")
            for (e <- orderCategoryIdsSplited) {
              //TODO
              list += ((e.toLong, e.toLong))
            }
          }
        }
        case None => {}
      }
      //      if (orderCategoryIds != null) {
      //        val orderCategoryIdsSplited: Array[String] = orderCategoryIds.split(",")
      //        for (e <- orderCategoryIdsSplited) {
      //          list += ((Long.valueOf(e), Long.valueOf(e)))
      //        }
      //      }
      val payCategoryIds: Option[String] = Option(row.getString(10))
      payCategoryIds match {
        case Some(str) => {
          if (!str.equals("")) {
            val payCategoryIdsSplited: Array[String] = str.split(",")
            for (e <- payCategoryIdsSplited) {
              list += ((e.toLong, e.toLong))
            }
          }
        }
        case None => {}
      }

      list
    }
    )

    /*
     * 第二步,计算各品类的点击,下单和支付次数
     */

    /**
      * 获取各品类点击次数RDD
      */
    val clickCategoryId2CountRDD = session2detailRDD.filter(tuple => {
      val row: Row = tuple._2
      var clickCategoryId: Long = 0L
      try {
        clickCategoryId = row.getLong(6)
      } catch {
        case e: Exception => clickCategoryId = 0L
      }
      clickCategoryId != 0L
    }).map(tuple => {
      val clickCategoryId: Long = tuple._2.getLong(6)
      (clickCategoryId, 1L)
    }).reduceByKey((x, y) => x + y)

    /**
      * 获取各品类的下单次数RDD
      */
    val OrderCategoryId2CountRDD = session2detailRDD.filter(tuple => {
      val row: Row = tuple._2
      row.getString(8) != null && !row.getString(8).equals("")
    }).flatMap(tuple => {
      val row: Row = tuple._2
      val orderCategoryIds = row.getString(8)
      val orderCategoryIdsSplited = orderCategoryIds.split(",")
      val list = ArrayBuffer[(Long, Long)]()
      for (e <- orderCategoryIdsSplited) {
        list += ((e.toLong, 1L))
      }
      list
    }).reduceByKey(_ + _)

    /**
      * 获取各个品类支付次数RDD
      */
    val PayCategoryId2CountRDD = session2detailRDD.filter(tuple => {
      val row: Row = tuple._2
      row.getString(10) != null && !row.getString(10).equals("")
    }).flatMap(tuple => {
      val row: Row = tuple._2
      val payCategoryIds = row.getString(10)
      val payCategoryIdsSplited = payCategoryIds.split(",")
      val list = ArrayBuffer[(Long, Long)]()
      for (e <- payCategoryIdsSplited) {
        list += ((e.toLong, 1))
      }
      list
    }).reduceByKey(_ + _)

    /**
      * 连接品类RDD与所有RDD品类的数据
      */
    val joinCategoryAndData = categoryidRDD
      .distinct()
      .leftOuterJoin(clickCategoryId2CountRDD)
      .map(tuple => {
        val categoryid: Long = tuple._1
        val optional: Option[Long] = tuple._2._2
        var clickCount: Long = 0L
        optional match {
          case Some(str) => clickCount = str
          case None => clickCount = 0L
        }
        val value = "categoryid=" + categoryid + "|" + "clickCount=" + clickCount
        (categoryid, value)
      })
      .leftOuterJoin(OrderCategoryId2CountRDD)
      .map(tuple => {
        val categoryid = tuple._1
        val value = tuple._2._1
        var orderCount = 0L
        val optional: Option[Long] = tuple._2._2
        optional match {
          case Some(str) => orderCount = str
          case None => orderCount = 0L
        }
        val newValue = value + "|" + "orderCount=" + orderCount
        (categoryid, newValue)
      })
      .leftOuterJoin(PayCategoryId2CountRDD)
      .map(tuple => {
        val categoryid = tuple._1
        val value = tuple._2._1
        var payCount = 0L
        val optional: Option[Long] = tuple._2._2
        optional match {
          case Some(str) => payCount = str
          case None => payCount = 0L
        }
        val newValue = value + "|" + "payCount=" + payCount
        (categoryid, newValue)
      })


    //(39,categoryid=39|clickCount=32|orderCount=8|payCount=9)
    /**
      * 第四步:自定义二次排序
      */

    /**
      * 第五步 将数据映射成<CategorySortKey,Info>
      */
    val sortKey2countRDD = joinCategoryAndData.map(tuple => {
      val countInfo = tuple._2
      val clickCount = (StringUtils.getFieldFromConcatString(countInfo, "\\|", "clickCount").orNull).toLong
      val orderCount = (StringUtils.getFieldFromConcatString(countInfo, "\\|", "orderCount").orNull).toLong
      val payCount = (StringUtils.getFieldFromConcatString(countInfo, "\\|", "payCount").orNull).toLong

      val sortKey = new SortKey(clickCount, orderCount, payCount)
      (sortKey, countInfo)
    }).sortByKey(false)

    val top10CategoryList = sortKey2countRDD.take(10)

    top10CategoryList
  }


}
