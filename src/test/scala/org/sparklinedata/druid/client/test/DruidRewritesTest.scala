/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sparklinedata.druid.client.test

import com.github.nscala_time.time.Imports._
import org.apache.spark.sql.catalyst.dsl.expressions._
import org.sparklinedata.druid.{DruidQuery, GroupByQuerySpec, SearchQuerySpec}
import org.sparklinedata.spark.dateTime.dsl.expressions._

import scala.language.postfixOps

class DruidRewritesTest extends BaseTest {

  test("profile",
    "select count(distinct o_custkey) from orderLineItemPartSupplier",
    1,
    true,
    true
  )

  test("basicAggB",
    "select l_returnflag, l_linestatus, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a," +
      "count(distinct o_orderkey)  " +
      "from orderLineItemPartSupplierBase group by l_returnflag, l_linestatus",
    0,
    true
  )

  test("basicAgg",
      "select l_returnflag, l_linestatus, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a," +
      "count(distinct o_orderkey)  " +
      "from orderLineItemPartSupplier group by l_returnflag, l_linestatus",
    2,
    true
  )

  test("view",

    """
        |SELECT COUNT(DISTINCT CAST(`orderLineItemPartSupplier`.`l_shipdate` AS TIMESTAMP))
        | AS `ctd_date_string_ok`
        |FROM `orderLineItemPartSupplier`
        | HAVING (COUNT(1) > 0)
      """.stripMargin,
    2,
    true,
    true
  )

  test("noAggs",
      "select l_returnflag, l_linestatus " +
        "from orderLineItemPartSupplier " +
        "group by l_returnflag, l_linestatus",
    1,
    true,
    true
  )

  test("basicAggWithProject",
        "select f, s, " +
          "count(*)  " +
          "from (select l_returnflag f, l_linestatus s " +
          "from orderLineItemPartSupplier) t group by f, s",
    1,
    true,
    false,
    true
  )

  test("dateFilter", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)
    date"""
      select f, s, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate, s_region
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate
      group by f,s
      order by f,s
"""},
    1,
    true
  )

  test("intervalFilter", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)
    date"""
      select f, s, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate, s_region, s_nation, c_nation
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and ((s_nation = 'FRANCE' and c_nation = 'GERMANY') or
                                  (c_nation = 'FRANCE' and s_nation = 'GERMANY')
                                 )
      group by f,s
      order by f,s
"""
  },
    1, true
  )

  test("intervalFilter2", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)
    val shipDtPredicate2 = dateTime('l_shipdate) > (dateTime("1995-12-01"))

    date"""
      select f, s, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate, s_region, s_nation, c_nation
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and $shipDtPredicate2
      group by f,s
      order by f,s
"""
  },
    1,
    true
  )

  test("intervalFilter3", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)
    val shipDtPredicate2 = dateTime('l_shipdate) < (dateTime("1995-12-01"))

    date"""
      select f, s, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate, s_region, s_nation, c_nation
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and $shipDtPredicate2
      group by f,s
      order by f,s
"""
  },
    1,
    true)

  test("intervalFilter4", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)
    val shipDtPredicate2 = dateTime('l_shipdate) > (dateTime("1997-12-02"))

    date"""
      select f, s, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate, s_region, s_nation, c_nation
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and $shipDtPredicate2
      group by f,s
      order by f,s
"""
  },
    1,
    true
  )

  test("dimFilter2", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)

    date"""
      select f, s, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate,
         s_region, s_nation, c_nation, p_type
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and ((s_nation = 'FRANCE' and c_nation = 'GERMANY') or
                                  (c_nation = 'FRANCE' and s_nation = 'GERMANY')
                                 ) and p_type = 'ECONOMY ANODIZED STEEL'
      group by f,s
      order by f,s
"""
  },
    1,
    true,
    true
  )

  test("dimFilter3", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)

    date"""
      select s_nation, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate,
         s_region, s_nation, c_nation, p_type
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and ((s_nation = 'FRANCE' and c_nation = 'GERMANY') or
                                  (c_nation = 'FRANCE' and s_nation = 'GERMANY')
                                 )
      group by s_nation
      order by s_nation
"""
  },
    1,
    true
  )

  test("dimFilter4", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)

    date"""
      select s_nation, count(*) as count_order
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate,
         s_region, s_nation, c_nation, p_type
         from orderLineItemPartSupplier
      ) t
      where $shipDtPredicate and s_nation >= 'FRANCE'
      group by s_nation
      order by s_nation
"""
  },
    1,
    true
  )

  test("projFilterAgg", {
    val shipDtPredicate = dateTime('l_shipdate) <= (dateTime("1997-12-01") - 90.day)
    val shipDtPredicate2 = dateTime('l_shipdate) > (dateTime("1995-12-01"))

    date"""
      select s_nation,
      count(*) as count_order,
      sum(l_extendedprice) as s,
      max(ps_supplycost) as m,
      avg(ps_availqty) as a,
      count(distinct o_orderkey)
      from
      (
         select l_returnflag as f, l_linestatus as s, l_shipdate,
         s_region, s_nation, c_nation, p_type,
         l_extendedprice, ps_supplycost, ps_availqty, o_orderkey
         from orderLineItemPartSupplier
         where p_type = 'ECONOMY ANODIZED STEEL'
      ) t
      where $shipDtPredicate and
            $shipDtPredicate2 and ((s_nation = 'FRANCE' and c_nation = 'GERMANY') or
                                  (c_nation = 'FRANCE' and s_nation = 'GERMANY')
                                 )
      group by s_nation
      order by s_nation
"""
  },
    2,
    true
  )

  test("ShipDateYearAgg", {

    val shipDtYrGroup = dateTime('l_shipdate) year

    date"""select l_returnflag, l_linestatus, $shipDtYrGroup, count(*),
      sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a,
      count(distinct o_orderkey)
      from orderLineItemPartSupplier group by l_returnflag, l_linestatus, $shipDtYrGroup"""
  },
    2,
    true
  )

  test("OrderDateYearAgg", {

    val orderDtYrGroup = dateTime('o_orderdate) year

    date"""select l_returnflag, l_linestatus, $orderDtYrGroup, count(*),
      sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a,
      count(distinct o_orderkey)
      from orderLineItemPartSupplier group by l_returnflag, l_linestatus, $orderDtYrGroup"""
  },
    2,
    true
  )

  test("noRewrite",
    """select *
        |from orderLineItemPartSupplier
        |limit 3""".stripMargin,
    0,
    true
    )

  test("noMetricsCName", {
    """select c_name
      from orderLineItemPartSupplier group by c_name"""
  },
    1,
    true, true
  )

  test("noMetricsCNameSort", {
    """select c_name
      from orderLineItemPartSupplier group by c_name
    order by c_name"""
  },
    1,
    true, true,
    false,
    Seq(
      { dq : DruidQuery =>
        dq.q.isInstanceOf[SearchQuerySpec]
      }
    )
  )

  test("noMetricsPSize", {
    """select p_size
      from orderLineItemPartSupplier group by p_size"""
  },
    1,
    true, true,
    false,
    Seq(
      { dq : DruidQuery => dq.q.isInstanceOf[SearchQuerySpec]}
    )
  )

  test("noMetricsPRetailPrice", {
    """select p_retailprice
      from orderLineItemPartSupplier group by p_retailprice"""
  },
    1,
    true,
    true,
    false,
    Seq(
      { dq : DruidQuery => dq.q.isInstanceOf[SearchQuerySpec]}
    )
  )

  test("noMetricsYearExpr", {

    val orderDtYrGroup = dateTime('o_orderdate) year

    date"""select $orderDtYrGroup
      from orderLineItemPartSupplier group by $orderDtYrGroup"""
  },
    1,
    true, true
  )

  test("noMetricsTimestamp", {

    val orderDtYrGroup = dateTime('o_orderdate) year

    date"""select Cast(Concat(To_date(l_shipdate), ' 00:00:00') AS TIMESTAMP)
      from orderLineItemPartSupplier
      group by Cast(Concat(To_date(l_shipdate), ' 00:00:00') AS TIMESTAMP)"""
  },
    1,
    true, true
  )

  test("noMetricsCNameSortWithIntervalFilter", {
    """select c_name
      from orderLineItemPartSupplier
      where l_shipdate > '1994'
      group by c_name
    order by c_name"""
  },
    1,
    true, true,
    false,
    Seq(
      { dq : DruidQuery =>
        dq.q.isInstanceOf[GroupByQuerySpec]
      }
    )
  )

  // scalastyle:off line.size.limit
  test("countDistinctAndJS",
  """
    |SELECT CAST((MONTH(CAST(`sp_ad_demo_qube`.`l_shipdate` AS TIMESTAMP)) - 1) / 3 + 1 AS BIGINT) AS `qr_l_shipdate_ok`,
    |COUNT(DISTINCT `sp_ad_demo_qube`.`s_region`) AS `usr_calculation_622270835564429312_ok`,
    |YEAR(CAST(`sp_ad_demo_qube`.`l_shipdate` AS TIMESTAMP)) AS `yr_l_shipdate_ok`
    |FROM `default`.`orderLineItemPartSupplier` `sp_ad_demo_qube`
    |WHERE ((CAST(`sp_ad_demo_qube`.`l_shipdate` AS TIMESTAMP) >= CAST('2016-02-29 16:00:00' AS TIMESTAMP)) AND
    |(CAST(`sp_ad_demo_qube`.`l_shipdate` AS TIMESTAMP) <= CAST('2016-03-27 16:00:00' AS TIMESTAMP)))
    |GROUP BY CAST((MONTH(CAST(`sp_ad_demo_qube`.`l_shipdate` AS TIMESTAMP)) - 1) / 3 + 1 AS BIGINT),
    |YEAR(CAST(`sp_ad_demo_qube`.`l_shipdate` AS TIMESTAMP))
  """.stripMargin,
    1,
    true,
    true
  )

  test("aggExprIncludesGByExpr",
  """
    |SELECT SUM(`sparkline_paysafe`.`l_extendedprice`) AS `temp_tc___1602391293__0_`,
    |SUM(`sparkline_paysafe`.`l_extendedprice`) AS `temp_temp_tc___4123911036__0___3760310399__0_`,
    |MIN(CAST(CAST(CAST(`sparkline_paysafe`.`l_shipdate` AS TIMESTAMP) AS TIMESTAMP) AS TIMESTAMP))
    |  AS `temp_temp_tc___4123911036__0___4190136418__0_`,
    |  CAST(`sparkline_paysafe`.`l_shipdate` AS TIMESTAMP) AS `l_shipdate`
    |  FROM `default`.`orderLineItemPartSupplier` `sparkline_paysafe`
    |  GROUP BY CAST(`sparkline_paysafe`.`l_shipdate` AS TIMESTAMP)
  """.stripMargin,
    1,
    true,
    true)

  test("topN",
    "select l_returnflag, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a " +
      "from orderLineItemPartSupplier group by l_returnflag " +
      "order by max(ps_supplycost) " +
      "limit 200",
    1,
    true,
    true,
    false,
    Seq(
      isTopN _,
      TopNThresholdCheck(200)
    )
  )

  test("topNDesc",
    "select l_returnflag, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a " +
      "from orderLineItemPartSupplier group by l_returnflag " +
      "order by max(ps_supplycost) desc " +
      "limit 200",
    1,
    true,
    true,
    false,
    Seq(
      isTopN _,
      TopNThresholdCheck(200)
    )
  )

  test("topNNotPushedLargeLimit",
    "select l_returnflag, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a " +
      "from orderLineItemPartSupplier group by l_returnflag " +
      "order by max(ps_supplycost) " +
      "limit 100001",
    1,
    false,
    false,
    false,
    Seq(
      isGBy _
    )
  )

  test("topNNotPushedMultiDim",
    "select l_returnflag, l_linestatus, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a " +
      "from orderLineItemPartSupplier " +
      "group by l_returnflag, l_linestatus " +
      "order by max(ps_supplycost) " +
      "limit 10",
    1,
    false,
    false,
    false,
    Seq(
      isGBy _
    )
  )

  test("topNNotPushedOByDim",
    "select l_returnflag, " +
      "count(*), sum(l_extendedprice) as s, max(ps_supplycost) as m, avg(ps_availqty) as a " +
      "from orderLineItemPartSupplier " +
      "group by l_returnflag " +
      "order by l_returnflag " +
      "limit 10",
    1,
    false,
    false,
    false,
    Seq(
      isGBy _
    )
  )

  test("countSName",
    "select " +
      "count(s_name) " +
      "from orderLineItemPartSupplier " +
      "limit 10",
    1,
    true,
    true,
    false
  )

}

