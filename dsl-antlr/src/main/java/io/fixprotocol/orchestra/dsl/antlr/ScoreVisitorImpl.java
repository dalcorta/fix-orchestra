/*
 * Copyright 2017 FIX Protocol Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package io.fixprotocol.orchestra.dsl.antlr;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.AddSubContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.AnyExpressionContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.AssignmentContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.CharacterContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.ContainsContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.DateonlyContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.DecimalContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.DurationContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.EqualityContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.ExistContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.ExprContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.IndexContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.IntegerContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.LogicalAndContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.LogicalNotContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.LogicalOrContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.MulDivContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.ParensContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.PredContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.QualContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.RangeContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.RelationalContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.StringContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.TimeonlyContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.TimestampContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.UnaryMinusContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.VarContext;
import io.fixprotocol.orchestra.dsl.antlr.ScoreParser.VariableContext;
import io.fixprotocol.orchestra.dsl.datetime.DateTimeFormatters;
import io.fixprotocol.orchestra.model.FixNode;
import io.fixprotocol.orchestra.model.FixType;
import io.fixprotocol.orchestra.model.FixValue;
import io.fixprotocol.orchestra.model.FixValueFactory;
import io.fixprotocol.orchestra.model.FixValueOperations;
import io.fixprotocol.orchestra.model.ModelException;
import io.fixprotocol.orchestra.model.PathStep;
import io.fixprotocol.orchestra.model.Scope;
import io.fixprotocol.orchestra.model.SymbolResolver;


/**
 * Evaluates Score DSL expressions
 *
 * @author Don Mendelson
 *
 */
class ScoreVisitorImpl extends AbstractParseTreeVisitor<FixValue<?>>
    implements ScoreVisitor<FixValue<?>> {

  private Scope currentScope;
  private final SemanticErrorListener errorListener;

  private final FixValueOperations fixValueOperations = new FixValueOperations();

  private PathStep pathStep;

  private final SymbolResolver symbolResolver;


  private boolean trace = false;

  /**
   * Constructor with default SemanticErrorListener
   *
   * @param symbolResolver resolves symbols in variable and message spaces
   */
  public ScoreVisitorImpl(SymbolResolver symbolResolver) {
    this(symbolResolver, new BaseSemanticErrorListener());
  }

  /**
   * Constructor
   *
   * @param symbolResolver resolves symbols in variable and message spaces
   * @param errorListener listens for semantic errors
   */
  public ScoreVisitorImpl(SymbolResolver symbolResolver, SemanticErrorListener errorListener) {
    this.symbolResolver = symbolResolver;
    this.errorListener = errorListener;
  }

  /**
   * @return the trace
   */
  public boolean isTrace() {
    return trace;
  }


  /**
   * @param trace the trace to set
   */
  public void setTrace(boolean trace) {
    this.trace = trace;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitAddSub(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.AddSubContext)
   */
  @Override
  public FixValue<?> visitAddSub(AddSubContext ctx) {
    final FixValue<?> operand0 = visit(ctx.expr(0));
    final FixValue<?> operand1 = visit(ctx.expr(1));

    try {
      switch (ctx.op.getText()) {
        case "+":
          return fixValueOperations.add.apply(operand0, operand1);
        case "-":
          return fixValueOperations.subtract.apply(operand0, operand1);
      }
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitAnyExpression(io.fixprotocol.orchestra.dsl
   * .antlr.ScoreParser.AnyExpressionContext)
   */
  @Override
  public FixValue<?> visitAnyExpression(AnyExpressionContext ctx) {
    return visitChildren(ctx);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitAssignment(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.AssignmentContext)
   */
  @Override
  public FixValue<?> visitAssignment(AssignmentContext ctx) {
    final FixValue<?> val = visit(ctx.expr());
    if (val == null) {
      errorListener.onError(
          String.format("Semantic error; missing val for assignment at '%s'", ctx.getText()));
      return null;
    }
    final FixValue<?> var = visitVar(ctx.var());
    try {
      if (var != null) {
        var.assign(val);
        return var;
      } else {
        final FixValue<?> namedVal = FixValueFactory.copy(pathStep.getName(), val);
        return currentScope.assign(pathStep, namedVal);
      }
    } catch (final ModelException e) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", e.getMessage(), ctx.getText()));
      return null;
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitCharacter(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.CharacterContext)
   */
  @Override
  public FixValue<?> visitCharacter(CharacterContext ctx) {
    return new FixValue<Character>(FixType.charType, ctx.CHAR().getText().charAt(1));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitContains(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.ContainsContext)
   */
  @Override
  public FixValue<?> visitContains(ContainsContext ctx) {
    final FixValue<?> operand0 = visit(ctx.val);
    for (final ExprContext memberExpr : ctx.member) {
      final FixValue<?> member = visit(memberExpr);
      final FixValue<Boolean> result = fixValueOperations.eq.apply(operand0, member);
      if (result.getValue()) {
        return result;
      }
    }

    return new FixValue<Boolean>(FixType.BooleanType, Boolean.FALSE);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitDateonly(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.DateonlyContext)
   */
  @Override
  public FixValue<?> visitDateonly(DateonlyContext ctx) {
    return new FixValue<LocalDate>(FixType.UTCDateOnly, LocalDate.parse(ctx.DATE().getText()));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitDecimal(io.fixprotocol.orchestra.dsl.antlr
   * .ScoreParser.DecimalContext)
   */
  @Override
  public FixValue<?> visitDecimal(DecimalContext ctx) {
    return new FixValue<BigDecimal>(FixType.floatType, new BigDecimal(ctx.DECIMAL().getText()));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitDuration(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.DurationContext)
   */
  @Override
  public FixValue<?> visitDuration(DurationContext ctx) {
    return new FixValue<Duration>(FixType.Duration, Duration.parse(ctx.PERIOD().getText()));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitEquality(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.EqualityContext)
   */
  @Override
  public FixValue<Boolean> visitEquality(EqualityContext ctx) {
    final FixValue<?> operand0 = visit(ctx.expr(0));
    final FixValue<?> operand1 = visit(ctx.expr(1));

    try {
      switch (ctx.op.getText()) {
        case "==":
        case "eq":
          return fixValueOperations.eq.apply(operand0, operand1);
        case "!=":
        case "ne":
          return fixValueOperations.ne.apply(operand0, operand1);
      }
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;

  }

  @Override
  public FixValue<?> visitExist(ExistContext ctx) {
    final FixValue<Boolean> result = new FixValue<Boolean>("", FixType.BooleanType);
    final FixValue<?> var = visit(ctx.var());
    result.setValue(var != null);
    return result;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitIndex(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.IndexContext)
   */
  @Override
  public FixValue<?> visitIndex(IndexContext ctx) {
    if (ctx.UINT() != null) {
      pathStep.setIndex(Integer.parseInt(ctx.UINT().getText()));
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitInteger(io.fixprotocol.orchestra.dsl.antlr
   * .ScoreParser.IntegerContext)
   */
  @Override
  public FixValue<?> visitInteger(IntegerContext ctx) {
    return new FixValue<Integer>(FixType.intType, Integer.parseInt(ctx.UINT().getText()));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitLogicalAnd(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.LogicalAndContext)
   */
  @SuppressWarnings("unchecked")
  @Override
  public FixValue<Boolean> visitLogicalAnd(LogicalAndContext ctx) {
    final FixValue<Boolean> operand0 = (FixValue<Boolean>) visit(ctx.expr(0));
    final FixValue<Boolean> operand1 = (FixValue<Boolean>) visit(ctx.expr(1));
    try {
      switch (ctx.op.getText()) {
        case "&&":
        case "and":
          return fixValueOperations.and.apply(operand0, operand1);
      }
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;
  }

  @Override
  public FixValue<Boolean> visitLogicalNot(LogicalNotContext ctx) {
    @SuppressWarnings("unchecked")
    final FixValue<Boolean> operand = (FixValue<Boolean>) visit(ctx.expr());
    try {
      return fixValueOperations.not.apply(operand);
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitLogicalOr(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.LogicalOrContext)
   */
  @Override
  public FixValue<Boolean> visitLogicalOr(LogicalOrContext ctx) {
    @SuppressWarnings("unchecked")
    final FixValue<Boolean> operand0 = (FixValue<Boolean>) visit(ctx.expr(0));
    @SuppressWarnings("unchecked")
    final FixValue<Boolean> operand1 = (FixValue<Boolean>) visit(ctx.expr(1));

    try {
      switch (ctx.op.getText()) {
        case "||":
        case "or":
          return fixValueOperations.or.apply(operand0, operand1);
      }
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitMulDiv(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.MulDivContext)
   */
  @Override
  public FixValue<?> visitMulDiv(MulDivContext ctx) {
    final FixValue<?> operand0 = visit(ctx.expr(0));
    final FixValue<?> operand1 = visit(ctx.expr(1));

    try {
      switch (ctx.op.getText()) {
        case "*":
          return fixValueOperations.multiply.apply(operand0, operand1);
        case "/":
          return fixValueOperations.divide.apply(operand0, operand1);
        case "%":
        case "mod":
          return fixValueOperations.mod.apply(operand0, operand1);

      }
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitParens(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.ParensContext)
   */
  @Override
  public FixValue<?> visitParens(ParensContext ctx) {
    return visit(ctx.expr());
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitPred(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.PredContext)
   */
  @Override
  public FixValue<?> visitPred(PredContext ctx) {
    final TerminalNode id = ctx.ID();
    final ExprContext expr = ctx.expr();
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitQual(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.QualContext)
   */
  @Override
  public FixValue<?> visitQual(QualContext ctx) {
    pathStep = new PathStep(ctx.ID().getText());

    final IndexContext indexContext = ctx.index();
    if (indexContext != null) {
      visitIndex(indexContext);
    }
    final PredContext predContext = ctx.pred();
    if (predContext != null) {
      final String id = predContext.ID().getText();
      final ExprContext expr = predContext.expr();
      // todo evaluate predicate expression
    }

    final FixNode node = currentScope.resolve(pathStep);
    if (node instanceof Scope) {
      currentScope = (Scope) node;
      if (isTrace()) {
        System.out.format("Current scope %s%n", currentScope.getName());
      }
      return null;
    } else if (node == null) {
      return null;
    } else {
      return (FixValue<?>) node;
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitRange(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.RangeContext)
   */
  @Override
  public FixValue<?> visitRange(RangeContext ctx) {
    final FixValue<?> val = visit(ctx.val);
    final FixValue<?> min = visit(ctx.min);
    final FixValue<?> max = visit(ctx.max);

    return fixValueOperations.and.apply(fixValueOperations.ge.apply(val, min),
        fixValueOperations.le.apply(val, max));
  }



  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitRelational(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.RelationalContext)
   */
  @Override
  public FixValue<Boolean> visitRelational(RelationalContext ctx) {
    final FixValue<?> operand0 = visit(ctx.expr(0));
    final FixValue<?> operand1 = visit(ctx.expr(1));

    try {
      switch (ctx.op.getText()) {
        case "<":
        case "lt":
          return fixValueOperations.lt.apply(operand0, operand1);
        case "<=":
        case "le":
          return fixValueOperations.le.apply(operand0, operand1);
        case ">":
        case "gt":
          return fixValueOperations.gt.apply(operand0, operand1);
        case ">=":
        case "ge":
          return fixValueOperations.ge.apply(operand0, operand1);
      }
    } catch (final Exception ex) {
      errorListener
          .onError(String.format("Semantic error; %s at '%s'", ex.getMessage(), ctx.getText()));
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitString(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.StringContext)
   */
  @Override
  public FixValue<?> visitString(StringContext ctx) {
    final String text = ctx.STRING().getText();
    return new FixValue<String>(FixType.StringType, text.substring(1, text.length() - 1));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitTimeonly(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.TimeonlyContext)
   */
  @Override
  public FixValue<?> visitTimeonly(TimeonlyContext ctx) {
    // Remove initial T and timeztamp for Java, even though ISO require them
    return new FixValue<LocalTime>(FixType.UTCTimeOnly,
        LocalTime.parse(ctx.TIME().getText(), DateTimeFormatters.TIME_ONLY));
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitTimestamp(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.TimestampContext)
   */
  @Override
  public FixValue<?> visitTimestamp(TimestampContext ctx) {
    final Instant instant =
        DateTimeFormatters.DATE_TIME.parse(ctx.DATETIME().getText(), Instant::from);
    return new FixValue<Instant>(FixType.UTCTimestamp, instant);
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitUnaryNeg(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.UnaryNegContext)
   */
  @SuppressWarnings("unchecked")
  @Override
  public FixValue<?> visitUnaryMinus(UnaryMinusContext ctx) {
    final FixValue<?> unsigned = visit(ctx.expr());
    final Object val = unsigned.getValue();
    if (val instanceof Integer) {
      ((FixValue<Integer>) unsigned).setValue((Integer) val * -1);
    } else if (val instanceof BigDecimal) {
      ((FixValue<BigDecimal>) unsigned)
          .setValue(((BigDecimal) val).multiply(BigDecimal.valueOf(-1)));
    } else {
      errorListener.onError(
          String.format("Semantic error; cannot apply unary minus at '%s'", ctx.getText()));
    }
    return unsigned;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitVar(io.fixprotocol.orchestra.dsl.antlr.
   * ScoreParser.VarContext)
   */
  @Override
  public FixValue<?> visitVar(VarContext ctx) {
    FixValue<?> value = null;
    currentScope = symbolResolver;
    String scopeText;
    if (ctx.scope == null) {
      // implicit scope
      scopeText = "this.";
    } else {
      scopeText = ctx.scope.getText();
    }
    pathStep = new PathStep(scopeText);
    final FixNode node = currentScope.resolve(pathStep);
    if (node instanceof Scope) {
      currentScope = (Scope) node;
      if (isTrace()) {
        System.out.format("Current scope %s%n", currentScope.getName());
      }
      final List<QualContext> qualifiers = ctx.qual();
      for (final QualContext qualifier : qualifiers) {
        value = visitQual(qualifier);
      }
    } else {
      errorListener.onError(
          String.format("Unknown symbol scope; %s at '%s'", pathStep.getName(), ctx.getText()));
    }
    return value;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * io.fixprotocol.orchestra.dsl.antlr.ScoreVisitor#visitVariable(io.fixprotocol.orchestra.dsl.
   * antlr.ScoreParser.VariableContext)
   */
  @Override
  public FixValue<?> visitVariable(VariableContext ctx) {
    return visit(ctx.var());
  }

}
