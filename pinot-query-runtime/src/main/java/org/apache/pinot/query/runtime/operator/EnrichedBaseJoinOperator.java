package org.apache.pinot.query.runtime.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import javax.annotation.Nullable;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.core.query.selection.SelectionOperatorUtils;
import org.apache.pinot.query.planner.logical.RexExpression;
import org.apache.pinot.query.planner.plannode.EnrichedJoinNode;
import org.apache.pinot.query.runtime.blocks.MseBlock;
import org.apache.pinot.query.runtime.operator.operands.TransformOperand;
import org.apache.pinot.query.runtime.operator.operands.TransformOperandFactory;
import org.apache.pinot.query.runtime.operator.utils.SortUtils;
import org.apache.pinot.query.runtime.plan.OpChainExecutionContext;
import org.apache.pinot.spi.utils.CommonConstants;


public class EnrichedBaseJoinOperator extends BaseJoinOperator {
  private static final String EXPLAIN_NAME = "ENRICHED_JOIN";
  @Nullable
  private final TransformOperand _filterOperand;
  @Nullable
  private final List<TransformOperand> _projectOperands;
  @Nullable
  private final PriorityQueue<Object[]> _priorityQueue;
  private final int _offset;
  private final int _numRowsToKeep;
  private final DataSchema _joinResultSchema;
  private final DataSchema _projectResultSchema;

  public EnrichedBaseJoinOperator(OpChainExecutionContext context,
      MultiStageOperator leftInput, DataSchema leftSchema, MultiStageOperator rightInput,
      EnrichedJoinNode node) {
    super(context, leftInput, leftSchema, rightInput, node);

    _joinResultSchema = node.getJoinResultSchema();
    _projectResultSchema = node.getProjectResultSchema();

    // input of filter is join result
    _filterOperand = node.getFilterCondition() == null ?
        null : TransformOperandFactory.getTransformOperand(node.getFilterCondition(), _joinResultSchema);

    List<RexExpression> projectExpressions = node.getProjects();
    if (projectExpressions == null) {
      _projectOperands = null;
    } else {
      _projectOperands = new ArrayList<>();
      // input of project is filter result, which has same schema as join result
      projectExpressions.forEach( (x) -> {
          _projectOperands.add(TransformOperandFactory.getTransformOperand(x, _projectResultSchema));
        });
    }

    _offset = Math.max(node.getOffset(), 0);
    int fetch = node.getFetch();

    // TODO: see if this need to be converted to input args
    int defaultHolderCapacity = SelectionOperatorUtils.MAX_ROW_HOLDER_INITIAL_CAPACITY;
    int defaultResponseLimit = CommonConstants.Broker.DEFAULT_BROKER_QUERY_RESPONSE_LIMIT;
    _numRowsToKeep = fetch > 0 ? fetch + _offset : defaultResponseLimit;

    List<RelFieldCollation> collations = node.getCollations();
    if (collations == null || collations.isEmpty()) {
      _priorityQueue = null;
    } else {
      // Use the opposite direction as specified by the collation directions since we need the PriorityQueue to decide
      // which elements to keep and which to remove based on the limits.
      _priorityQueue = new PriorityQueue<>(Math.min(defaultHolderCapacity, _numRowsToKeep),
          new SortUtils.SortComparator(collations, true));
    }
  }

  // TODO: implement next() logics

  @Override
  protected void onEosProduced() {

  }

  @Override
  protected void addRowsToRightTable(List<Object[]> rows) {

  }

  @Override
  protected void finishBuildingRightTable() {

  }

  @Override
  protected List<Object[]> buildJoinedRows(MseBlock.Data leftBlock) {
    return List.of();
  }

  @Override
  protected List<Object[]> buildNonMatchRightRows() {
    return List.of();
  }

  @Nullable
  @Override
  public String toExplainString() {
    return "";
  }
}
