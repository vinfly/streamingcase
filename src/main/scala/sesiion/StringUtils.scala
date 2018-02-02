package sesiion

/**
  * Created by mac on 17/10/17.
  */
object StringUtils {

  def fulfuill(str: String): String = {
    if (str.length == 2) str else "0" + str
  }

  def isEmpty(str: String): Boolean = {
    str == null || "".equals(str)
  }

  def isNotEmpty(str: String): Boolean = {
    str != null && !"".equals(str) && str != "null"
  }

  def trimComma(str: String): String = {
    if (str.startsWith(",")) str.substring(1)

    if (str.endsWith(",")) str.substring(0, str.length - 1)
    str
  }

  /**
    * 从拼接的字符串中提取字段
    */
  def getFieldFromConcatString(str: String, delimiter: String, field: String): Option[String] = {

    var value: Option[String] = None
    var flag = false
    val fields: Array[String] = str.split(delimiter)
    for (e <- fields) {
      if (e.split("=").length == 2 && !flag) {
        val fieldName = e.split("=")(0)
        val fieldValue = e.split("=")(1)
        if (fieldName.equals(field)) {
          flag = true
          value = Some(fieldValue)
        }
      }

    }

    value

  }

  def setFieldInConcatString(str: String, delimiter: String, field: String, newFieldValue: String): String = {
    var flag = false
    val fields: Array[String] = str.split(delimiter)

    for (i <- 0 until fields.length) {
      val fieldName = fields(i).split("=")(0)
      if (fieldName.equals(field) && !flag) {
        val concatField = fieldName + "=" + newFieldValue
        fields(i) = concatField
        flag = true
      }
    }

    val buffer: StringBuffer = new StringBuffer("")
    for (i <- 0 until fields.length) {
      buffer.append(fields(i))
      if (i < fields.length - 1) {
        buffer.append("|")
      }
    }

    buffer.toString
  }


}


