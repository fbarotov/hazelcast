/*
 * Copyright 2021 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.parse;

import com.hazelcast.jet.sql.impl.HazelcastSqlToRelConverter;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.HazelcastRelOptCluster;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCostImpl;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgramBuilder;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.sql2rel.StandardConvertletTable;
import org.apache.calcite.util.Pair;

/**
 * Converts a parse tree into a relational tree.
 */
public class QueryConverter {

    /**
     * Whether to expand subqueries. When set to {@code false}, subqueries are left as is in the form of
     * {@link org.apache.calcite.rex.RexSubQuery}. Otherwise they are expanded into {@link org.apache.calcite.rel.core.Correlate}
     * instances.
     * Do not enable this because you may run into https://issues.apache.org/jira/browse/CALCITE-3484. Instead, subquery
     * elimination rules are executed during logical planning. In addition, resulting plans are slightly better that those
     * produced by "expand" flag.
     */
    private static final boolean EXPAND = false;

    /** Whether to trim unused fields. The trimming is needed after subquery elimination. */
    private static final boolean TRIM_UNUSED_FIELDS = true;

    /** Increase the maximum number of elements in the RHS to convert the IN operator to a sequence of OR comparisons. */
    private static final int HAZELCAST_IN_ELEMENTS_THRESHOLD = 10_000;

    private static final SqlToRelConverter.Config CONFIG;

    static {
        CONFIG = SqlToRelConverter.configBuilder()
                .withExpand(EXPAND)
                .withInSubQueryThreshold(HAZELCAST_IN_ELEMENTS_THRESHOLD)
                .withTrimUnusedFields(TRIM_UNUSED_FIELDS)
                .build();
    }

    private final SqlValidator validator;
    private final Prepare.CatalogReader catalogReader;
    private final RelOptCluster cluster;

    public QueryConverter(SqlValidator validator, Prepare.CatalogReader catalogReader, HazelcastRelOptCluster cluster) {
        this.validator = validator;
        this.catalogReader = catalogReader;
        this.cluster = cluster;
    }

    public QueryConvertResult convert(SqlNode node) {
        SqlToRelConverter converter = new HazelcastSqlToRelConverter(
                null,
                validator,
                catalogReader,
                cluster,
                StandardConvertletTable.INSTANCE,
                CONFIG
        );

        // 1. Perform initial conversion.
        RelRoot root = converter.convertQuery(node, false, true);

        // 2. Remove subquery expressions, converting them to Correlate nodes.
        RelNode relNoSubqueries = rewriteSubqueries(root.project());

        // 3. Perform decorrelation, i.e. rewrite a nested loop where the right side depends on the value of the left side,
        // to a variation of joins, semijoins and aggregations, which could be executed much more efficiently.
        // See "Unnesting Arbitrary Queries", Thomas Neumann and Alfons Kemper.
        RelNode relDecorrelated = converter.decorrelate(node, relNoSubqueries);

        // 4. The side effect of subquery rewrite and decorrelation in Apache Calcite is a number of unnecessary fields,
        // primarily in projections. This steps removes unused fields from the tree.
        RelNode relTrimmed = converter.trimUnusedFields(true, relDecorrelated);

        // 5. Collect original field names.
        return new QueryConvertResult(relTrimmed, Pair.right(root.fields));
    }

    /**
     * Special substep of an initial query conversion which eliminates correlated subqueries, converting them to various forms
     * of joins. It is used instead of "expand" flag due to bugs in Calcite (see {@link #EXPAND}).
     *
     * @param rel Initial relation.
     * @return Resulting relation.
     */
    private static RelNode rewriteSubqueries(RelNode rel) {
        HepProgramBuilder hepPgmBldr = new HepProgramBuilder();

        hepPgmBldr.addRuleInstance(CoreRules.FILTER_SUB_QUERY_TO_CORRELATE);
        hepPgmBldr.addRuleInstance(CoreRules.PROJECT_SUB_QUERY_TO_CORRELATE);
        hepPgmBldr.addRuleInstance(CoreRules.JOIN_SUB_QUERY_TO_CORRELATE);

        HepPlanner planner = new HepPlanner(hepPgmBldr.build(), Contexts.empty(), true, null, RelOptCostImpl.FACTORY);

        planner.setRoot(rel);

        return planner.findBestExp();
    }
}
