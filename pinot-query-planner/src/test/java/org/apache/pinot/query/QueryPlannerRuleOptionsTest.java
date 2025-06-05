/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.query;

import java.util.HashMap;
import java.util.Map;
import org.apache.calcite.sql.SqlNode;
import org.apache.pinot.common.utils.config.QueryOptionsUtils;
import org.apache.pinot.spi.utils.CommonConstants;
import org.apache.pinot.sql.parsers.CalciteSqlParser;
import org.apache.pinot.sql.parsers.PinotSqlType;
import org.apache.pinot.sql.parsers.SqlNodeAndOptions;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


public class QueryPlannerRuleOptionsTest extends QueryEnvironmentTestBase {

  private String explainQueryWithRuleDisabled(String query, String skipRule) {
    SqlNode sqlNode = CalciteSqlParser.compileToSqlNodeAndOptions(query).getSqlNode();
    Map<String, String> options = new HashMap<>();
    // disable rule
    options.put(skipRule, "true");
    SqlNodeAndOptions sqlNodeAndOptions =
        new SqlNodeAndOptions(
            sqlNode,
            PinotSqlType.DQL,
            QueryOptionsUtils.resolveCaseInsensitiveOptions(options));
    return _queryEnvironment
        .compile(query, sqlNodeAndOptions)
        .explain(RANDOM_REQUEST_ID_GEN.nextLong(), null)
        .getExplainPlan();
  }

  private String explainQueryWithRuleEnabled(String query, String skipRule) {
    SqlNode sqlNode = CalciteSqlParser.compileToSqlNodeAndOptions(query).getSqlNode();
    Map<String, String> options = new HashMap<>();
    // disable rule
    options.put(skipRule, "false");
    SqlNodeAndOptions sqlNodeAndOptions =
        new SqlNodeAndOptions(
            sqlNode,
            PinotSqlType.DQL,
            QueryOptionsUtils.resolveCaseInsensitiveOptions(options));
    return _queryEnvironment
        .compile(query, sqlNodeAndOptions)
        .explain(RANDOM_REQUEST_ID_GEN.nextLong(), null)
        .getExplainPlan();
  }

  @Test
  public void testDisableCaseToFilter() {
    // Tests that when skipAggregateCaseToFilterRule=true,
    // CASE WHEN should not be optimized
    String query = "EXPLAIN PLAN FOR SELECT SUM(CASE WHEN col1 = 'a' THEN 1 ELSE 0 END) FROM a";
    String explain = explainQueryWithRuleDisabled(query,
        CommonConstants.Broker.Request.QueryOptionKey.RuleOptionKey.SKIP_AGGREGATE_CASE_TO_FILTER_RULE);

    //@formatter:off
    assertEquals(explain,
        "Execution Plan\n"
            + "LogicalProject(EXPR$0=[CASE(=($1, 0), null:BIGINT, $0)])\n"
            + "  PinotLogicalAggregate(group=[{}], agg#0=[$SUM0($0)], agg#1=[COUNT($1)], aggType=[FINAL])\n"
            + "    PinotLogicalExchange(distribution=[hash])\n"
            + "      PinotLogicalAggregate(group=[{}], agg#0=[$SUM0($0)], agg#1=[COUNT()], aggType=[LEAF])\n"
            + "        LogicalProject($f0=[CASE(=($0, _UTF-8'a'), 1, 0)])\n"
            + "          PinotLogicalTableScan(table=[[default, a]])\n");
    //@formatter:on
  }

  @Test
  public void testDisableReduceFunctions() {
    // Tests that when skipPinotAggregateReduceFunctionsRule=true,
    // SUM should not be reduced
    String query = "EXPLAIN PLAN FOR SELECT SUM(CASE WHEN col1 = 'a' THEN 3 ELSE 0 END) FROM a";

    String explain = explainQueryWithRuleDisabled(query,
        CommonConstants.Broker.Request.QueryOptionKey.RuleOptionKey.SKIP_PINOT_AGGREGATE_REDUCE_FUNCTIONS_RULE);
    //@formatter:off
    assertEquals(explain,
      "Execution Plan\n"
          + "PinotLogicalAggregate(group=[{}], agg#0=[SUM($0)], aggType=[FINAL])\n"
          + "  PinotLogicalExchange(distribution=[hash])\n"
          + "    PinotLogicalAggregate(group=[{}], agg#0=[SUM($0)], aggType=[LEAF])\n"
          + "      LogicalProject($f0=[CASE(=($0, _UTF-8'a'), 3, 0)])\n"
          + "        PinotLogicalTableScan(table=[[default, a]])\n");
    //@formatter:on
  }

  @Test
  public void testDisablePruneEmptyJoinLeft() {
    // Test that when skipPruneEmptyJoinLeft=true,
    String query = "EXPLAIN PLAN FOR SELECT *\n"
        + "FROM (\n"
        + "  SELECT * FROM a WHERE 1 = 0\n"
        + ") c\n"
        + "JOIN a ON c.col1 = a.col1 ;\n";

    String explain = explainQueryWithRuleDisabled(query,
        CommonConstants.Broker.Request.QueryOptionKey.RuleOptionKey.SKIP_PRUNE_EMPTY_JOIN_LEFT_RULE);
    //@formatter:off
    assertEquals(explain,
      "Execution Plan\n"
          + "LogicalJoin(condition=[=($0, $9)], joinType=[inner])\n"
          + "  PinotLogicalExchange(distribution=[hash[0]])\n"
          + "    LogicalValues(tuples=[[]])\n"
          + "  PinotLogicalExchange(distribution=[hash[0]])\n"
          + "    PinotLogicalTableScan(table=[[default, a]])\n");
    //@formatter:on
  }

  @Test
  public void testDisableJoinPushTransitivePredicate() {
    // queries involving extra predicate on join keys
    // should be optimized to push the predicate to both sides of the join if applicable
    String query = "EXPLAIN PLAN FOR\n"
        + "SELECT * FROM a\n"
        + "JOIN b\n"
        + "ON a.col1 = b.col1\n"
        + "WHERE a.col1 = 1;\n";

    String explain = explainQueryWithRuleDisabled(query,
        CommonConstants.Broker.Request.QueryOptionKey.RuleOptionKey.SKIP_JOIN_PUSH_TRANSITIVE_PREDICATES_RULE);
    //@formatter:off
    assertEquals(explain,
        "Execution Plan\n"
            + "LogicalJoin(condition=[=($0, $9)], joinType=[inner])\n"
            + "  PinotLogicalExchange(distribution=[hash[0]])\n"
            + "    LogicalFilter(condition=[=(CAST($0):INTEGER NOT NULL, 1)])\n"
            + "      PinotLogicalTableScan(table=[[default, a]])\n"
            + "  PinotLogicalExchange(distribution=[hash[0]])\n"
            + "    PinotLogicalTableScan(table=[[default, b]])\n");
    //@formatter:on
  }

  @Test
  public void testDisableAggregateJoinRemove() {
    // queries where join is left or right join and the aggregate above it has no aggCall
    // or all aggCalls are DISTINCT
    // should be optimized to remove the join completely
    String query = "EXPLAIN PLAN FOR\n"
        + "SELECT a.col1, COUNT(DISTINCT a.col3) \n"
        + "FROM a \n"
        + "LEFT JOIN b ON a.col2 = b.col2\n"
        + "GROUP BY a.col1;";

    String explain = explainQueryWithRuleDisabled(query,
        CommonConstants.Broker.Request.QueryOptionKey.RuleOptionKey.SKIP_AGGREGATE_JOIN_REMOVE_RULE);
    //@formatter:off
    assertEquals(explain,
        "Execution Plan\n"
            + "PinotLogicalAggregate(group=[{0}], agg#0=[DISTINCTCOUNT($1)], aggType=[FINAL])\n"
            + "  PinotLogicalExchange(distribution=[hash[0]])\n"
            + "    PinotLogicalAggregate(group=[{0}], agg#0=[DISTINCTCOUNT($2)], aggType=[LEAF])\n"
            + "      LogicalJoin(condition=[=($1, $3)], joinType=[left])\n"
            + "        PinotLogicalExchange(distribution=[hash[1]])\n"
            + "          LogicalProject(col1=[$0], col2=[$1], col3=[$2])\n"
            + "            PinotLogicalTableScan(table=[[default, a]])\n"
            + "        PinotLogicalExchange(distribution=[hash[0]])\n"
            + "          LogicalProject(col2=[$1])\n"
            + "            PinotLogicalTableScan(table=[[default, b]])\n");
    //@formatter:on
  }

  @Test
  public void testEnableAggregateJoinPushdownFunctions() {
    // queries where the aggCalls in aggregation above join is splitable could be
    // duplicated down the join, in specific scenario the above aggregate could be
    // completely removed
    String query = "EXPLAIN PLAN FOR \n"
        + "SELECT SUM(a.col1)\n"
        + "FROM b INNER JOIN a\n"
        + "ON b.col2 = a.col2\n"
        + "GROUP BY b.col2, a.col2";

    String explain = explainQueryWithRuleEnabled(query,
        CommonConstants.Broker.Request.QueryOptionKey.RuleOptionKey.SKIP_AGGREGATE_JOIN_TRANSPOSE_RULE_EXTENDED);
    //@formatter:off
    assertEquals(explain,
        "Execution Plan\n"
            + "LogicalProject(EXPR$0=[CAST(*($1, $3)):DECIMAL(2000, 1000) NOT NULL])\n"
            + "  LogicalJoin(condition=[=($0, $2)], joinType=[inner])\n"
            + "    PinotLogicalExchange(distribution=[hash[0]])\n"
            + "      PinotLogicalAggregate(group=[{0}], agg#0=[COUNT($1)], aggType=[FINAL])\n"
            + "        PinotLogicalExchange(distribution=[hash[0]])\n"
            + "          PinotLogicalAggregate(group=[{1}], agg#0=[COUNT()], aggType=[LEAF])\n"
            + "            PinotLogicalTableScan(table=[[default, b]])\n"
            + "    PinotLogicalExchange(distribution=[hash[0]])\n"
            + "      PinotLogicalAggregate(group=[{0}], agg#0=[$SUM0($1)], aggType=[FINAL])\n"
            + "        PinotLogicalExchange(distribution=[hash[0]])\n"
            + "          PinotLogicalAggregate(group=[{0}], agg#0=[$SUM0($1)], aggType=[LEAF])\n"
            + "            LogicalProject(col2=[$1], $f2=[CAST($0):DECIMAL(2000, 1000) NOT NULL])\n"
            + "              PinotLogicalTableScan(table=[[default, a]])\n");
    //@formatter:on
  }
}
