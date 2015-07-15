package org.sparklinedata.druid

import org.apache.spark.sql.catalyst.expressions.GenericRow
import org.apache.spark.{TaskContext, Partition}
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import org.joda.time.Interval
import org.sparklinedata.druid.client.DruidClient
import org.sparklinedata.druid.metadata.DruidRelationInfo

class DruidPartition(idx: Int, val i : Interval) extends Partition {
  override def index: Int = idx
}

class DruidRDD(sqlContext: SQLContext,
              val drInfo : DruidRelationInfo,
                val dQuery : DruidQuery)  extends  RDD[Row](sqlContext.sparkContext, Nil) {
  @DeveloperApi
  override def compute(split: Partition, context: TaskContext): Iterator[Row] = {

    val p = split.asInstanceOf[DruidPartition]
    val client = new DruidClient(drInfo.druidClientInfo.host, drInfo.druidClientInfo.port)
    val mQry = dQuery.q.setInterval(p.i)
    val r = client.executeQuery(mQry)
    val schema = dQuery.schema(drInfo)
    r.iterator.map { r =>
      new GenericRow(schema.fields.map(f => r.event(f.name)))
    }
  }

  override protected def getPartitions: Array[Partition] =
    dQuery.intervalSplits.zipWithIndex.map(t => new DruidPartition(t._2, t._1)).toArray
}
