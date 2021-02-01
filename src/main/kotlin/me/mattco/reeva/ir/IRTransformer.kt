package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.parser.Parser
import java.math.BigInteger

fun main() {
    val source = """
        let log = 1;
        console.log(a);
    """.trimIndent()
    val parsed = Parser(source).parseScript()
    ScopeAnalyzer().analyze(parsed)
    println("")
}

class FunctionInfo(
    val name: String?,
    val code: Array<Opcode>,
    val constantPool: Array<Any>,
    val registerCount: Int, // includes argCount
    val argCount: Int,
    val isTopLevelScript: Boolean = false,
)

class IRTransformer : ASTVisitor {
    private lateinit var builder: FunctionBuilder

    private val functionInfo = mutableListOf<FunctionInfo>()

    fun transform(node: ScriptNode): List<FunctionInfo> {
        if (::builder.isInitialized)
            throw IllegalStateException("Cannot re-use an IRTransformer")

        builder = FunctionBuilder()

        functionInfo.add(0, FunctionInfo(
            null,
            builder.opcodes.toTypedArray(),
            builder.constantPool.toTypedArray(),
            0,
            0,
            isTopLevelScript = true
        ))
        return functionInfo
    }

    override fun visitBlock(node: BlockNode) {
        TODO()
    }

    override fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)

        if (node.falseBlock == null) {
            val endLabel = label()
            jump(endLabel, ::JumpIfToBooleanFalse)
            visit(node.trueBlock)
            place(endLabel)
        } else {
            val falseLabel = label()
            val endLabel = label()
            jump(falseLabel, ::JumpIfToBooleanFalse)
            visit(node.trueBlock)
            jump(endLabel)
            place(falseLabel)
            visit(node.falseBlock)
            place(endLabel)
        }
    }

    override fun visitDoWhileStatement(node: DoWhileStatementNode) {
        val loopHead = label()
        place(loopHead)
        visit(node.body)
        visit(node.condition)
        jump(loopHead, ::JumpIfToBooleanTrue)
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        val loopHead = label()
        val loopEnd = label()

        place(loopHead)
        visit(node.condition)
        jump(loopEnd, ::JumpIfToBooleanFalse)
        visit(node.body)
        jump(loopHead)
        place(loopEnd)
    }

    override fun visitForStatement(node: ForStatementNode) {
        TODO()
    }

    override fun visitForIn(node: ForInNode) {
        TODO()
    }

    override fun visitForOf(node: ForOfNode) {
        TODO()
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        TODO()
    }

    override fun visitLabelledStatement(node: LabelledStatementNode) {
        TODO()
    }

    override fun visitThrowStatement(node: ThrowStatementNode) {
        TODO()
    }

    override fun visitTryStatement(node: TryStatementNode) {
        TODO()
    }

    override fun visitBreakStatement(node: BreakStatementNode) {
        TODO()
    }

    override fun visitReturnStatement(node: ReturnStatementNode) {
        TODO()
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        val isInlineable = node.isInlineable
        val isConst = node.isConst


    }

    override fun visitVariableStatement(node: VariableStatementNode) {
        TODO()
    }

    override fun visitDebuggerStatement() {
        TODO()
    }

    override fun visitImportDeclaration(node: ImportDeclarationNode) {
        TODO()
    }

    override fun visitExportDeclaration(node: ExportDeclarationNode) {
        TODO()
    }

    override fun visitBindingIdentifier(node: BindingIdentifierNode) {
        TODO()
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        TODO()
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        TODO()
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        TODO()
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        TODO()
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        TODO()
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        TODO()
    }

    private fun visitBinaryExpression(node: BinaryExpression, op: (Int) -> Opcode) {
        visit(node.lhs)
        val reg = nextFreeReg()
        markRegUsed(reg)
        +Star(reg)
        visit(node.rhs)
        +Add(reg)
        markRegFree(reg)
    }

    override fun visitAdditiveExpression(node: AdditiveExpressionNode) {
        visitBinaryExpression(node, ::Add)
    }

    override fun visitBitwiseANDExpression(node: BitwiseANDExpressionNode) {
        visitBinaryExpression(node, ::BitwiseAnd)
    }

    override fun visitBitwiseORExpression(node: BitwiseORExpressionNode) {
        visitBinaryExpression(node, ::BitwiseOr)
    }

    override fun visitBitwiseXORExpression(node: BitwiseXORExpressionNode) {
        visitBinaryExpression(node, ::BitwiseAnd)
    }

    override fun visitCoalesceExpression(node: CoalesceExpressionNode) {
        val rhsLabel = label()
        visit(node.lhs)
        jump(rhsLabel, ::JumpIfNullish)
        visit(node.rhs)
        place(rhsLabel)
    }

    override fun visitEqualityExpression(node: EqualityExpressionNode) {
        visit(node.lhs)
        val reg = nextFreeReg()
        markRegUsed(reg)
        +Star(reg)
        visit(node.rhs)

        when (node.op) {
            EqualityExpressionNode.Operator.StrictEquality -> +TestEqualStrict(reg)
            EqualityExpressionNode.Operator.StrictInequality -> +TestNotEqualStrict(reg)
            EqualityExpressionNode.Operator.NonstrictEquality -> +TestEqual(reg)
            EqualityExpressionNode.Operator.NonstrictInequality -> +TestNotEqual(reg)
        }

        markRegFree(reg)
    }

    override fun visitExponentiationExpression(node: ExponentiationExpressionNode) {
        visitBinaryExpression(node, ::Exp)
    }

    override fun visitLogicalANDExpression(node: LogicalANDExpressionNode) {
        visit(node.lhs)
        val rhsLabel = label()
        jump(rhsLabel, ::JumpIfToBooleanFalse)
        visit(node.rhs)
        place(rhsLabel)
    }

    override fun visitLogicalORExpression(node: LogicalORExpressionNode) {
        visit(node.lhs)
        val rhsLabel = label()
        jump(rhsLabel, ::JumpIfToBooleanTrue)
        visit(node.rhs)
        place(rhsLabel)
    }

    override fun visitMultiplicativeExpression(node: MultiplicativeExpressionNode) {
        visitBinaryExpression(node, when (node.op) {
            MultiplicativeExpressionNode.Operator.Multiply -> ::Mul
            MultiplicativeExpressionNode.Operator.Divide -> ::Div
            MultiplicativeExpressionNode.Operator.Modulo -> ::Mod
        })
    }

    override fun visitRelationalExpression(node: RelationalExpressionNode) {
        val reg = nextFreeReg()
        visit(node.lhs)
        +Star(reg)
        visit(node.rhs)

        when (node.op) {
            RelationalExpressionNode.Operator.LessThan -> +TestLessThan(reg)
            RelationalExpressionNode.Operator.GreaterThan -> +TestGreaterThan(reg)
            RelationalExpressionNode.Operator.LessThanEquals -> +TestLessThanOrEqual(reg)
            RelationalExpressionNode.Operator.GreaterThanEquals -> +TestGreaterThanOrEqual(reg)
            RelationalExpressionNode.Operator.Instanceof -> +TestInstanceOf(reg)
            RelationalExpressionNode.Operator.In -> +TestIn(reg)
        }

        markRegFree(reg)
    }

    override fun visitShiftExpression(node: ShiftExpressionNode) {
        val reg = nextFreeReg()
        visit(node.lhs)
        +Star(reg)
        visit(node.rhs)

        when (node.op) {
            ShiftExpressionNode.Operator.ShiftLeft -> +ShiftLeft(reg)
            ShiftExpressionNode.Operator.ShiftRight -> +ShiftRight(reg)
            ShiftExpressionNode.Operator.UnsignedShiftRight -> +ShiftRightUnsigned(reg)
        }

        markRegFree(reg)
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        visit(node.expression)

        when (node.op) {
            UnaryExpressionNode.Operator.Delete -> TODO()
            UnaryExpressionNode.Operator.Void -> +LdaUndefined
            UnaryExpressionNode.Operator.Typeof -> +TypeOf
            UnaryExpressionNode.Operator.Plus -> TODO()
            UnaryExpressionNode.Operator.Minus -> +Negate
            UnaryExpressionNode.Operator.BitwiseNot -> +BitwiseNot
            UnaryExpressionNode.Operator.Not -> +ToBooleanLogicalNot
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        TODO()
    }

    override fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        TODO()
    }

    override fun visitAwaitExpression(node: AwaitExpressionNode) {
        TODO()
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        val args = node.arguments.arguments

        if (node.target is MemberExpressionNode) {
            val callableReg = nextFreeReg()
            val receiverReg = nextFreeRegBlock(args.size + 1)
            visit(node.target.lhs)
            +Star(receiverReg)

            when (args.size) {
                0 -> +CallProperty0(callableReg, receiverReg)
                1 -> {
                    visit(args[0])
                    +Star(receiverReg + 1)
                    +CallProperty1(callableReg, receiverReg, receiverReg + 1)
                }
                else -> {
                    args.forEachIndexed { index, arg ->
                        visit(arg)
                        +Star(receiverReg + index + 1)
                    }
                    +CallProperty(callableReg, receiverReg, args.size)
                }
            }

            markRegFree(callableReg)
            for (i in args.indices)
                markRegFree(receiverReg + i)
        }
    }

    override fun visitCommaExpression(node: CommaExpressionNode) {
        node.expressions.forEach(::visit)
    }

    override fun visitConditionalExpression(node: ConditionalExpressionNode) {
        visit(node.predicate)

        val ifFalseLabel = label()
        val endLabel = label()

        jump(ifFalseLabel, ::JumpIfToBooleanFalse)
        visit(node.ifTrue)
        jump(endLabel)
        place(ifFalseLabel)
        visit(node.ifFalse)
        place(endLabel)
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        // TODO: Deal with assigning to a MemberExpression

        val objectReg = nextFreeReg()
        visit(node.lhs)
        +Star(objectReg)

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visit(node.rhs)
                +LdaKeyedProperty(objectReg)
            }
            MemberExpressionNode.Type.NonComputed -> {
                val cpIndex = loadConstant((node.rhs as IdentifierNode).identifierName)
                +LdaNamedProperty(objectReg, cpIndex)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }

        markRegFree(objectReg)
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        val target = nextFreeReg()
        visit(node.target)
        +Star(target)

        val regList = if (node.arguments != null) {
            loadArguments(node.arguments)
        } else null

        // TODO: Proper new.target
        +LdaUndefined

        if (regList != null) {
            +Construct(target, regList.firstReg, regList.count)
        } else +Construct0(target)

        markRegFree(target)
        regList?.markFree()
    }

    private fun loadArguments(arguments: ArgumentsNode): RegList {
        val firstReg = nextFreeRegBlock(arguments.arguments.size)
        arguments.arguments.forEachIndexed { index, argument ->
            if (argument.isSpread)
                TODO()
            visit(argument.expression)
            +Star(firstReg + index)
        }

        return RegList(firstReg, arguments.arguments.size).also {
            it.markUsed()
        }
    }

    override fun visitOptionalExpression(node: OptionalExpressionNode) {
        TODO()
    }

    override fun visitSuperPropertyExpression(node: SuperPropertyExpressionNode) {
        TODO()
    }

    override fun visitSuperCallExpression(node: SuperCallExpressionNode) {
        TODO()
    }

    override fun visitImportCallExpression(node: ImportCallExpressionNode) {
        TODO()
    }

    override fun visitYieldExpression(node: YieldExpressionNode) {
        TODO()
    }

    override fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        visit(node.expression)
    }

    override fun visitTemplateLiteral(node: TemplateLiteralNode) {
        TODO()
    }

    override fun visitRegExpLiteral(node: RegExpLiteralNode) {
        TODO()
    }

    override fun visitImportMetaExpression() {
        TODO()
    }

    override fun visitNewTargetExpression() {
        TODO()
    }

    override fun visitArrayLiteral(node: ArrayLiteralNode) {
        TODO()
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        TODO()
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        +LdaConstant(loadConstant(node.value))
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        +LdaConstant(loadConstant(node.value))
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        +LdaConstant(loadConstant(node.value))
    }

    override fun visitBigIntLiteral(node: BigIntLiteralNode) {
        val bigint = BigInteger(node.value, node.type.radix)
        +LdaConstant(loadConstant(bigint))
    }

    override fun visitNullLiteral() {
        +LdaNull
    }

    override fun visitThisLiteral() {
        +Ldar(receiverReg())
    }

    private fun getOpcode(index: Int) = builder.getOpcode(index)
    private fun setOpcode(index: Int, value: Opcode) = builder.setOpcode(index, value)
    private fun label() = builder.label()
    private fun jump(label: FunctionBuilder.Label, op: (Int) -> Opcode = ::Jump) = builder.jumpHelper(label, op)
    private fun place(label: FunctionBuilder.Label) = builder.place(label)
    private fun loadConstant(constant: Any) = builder.loadConstant(constant)
    private fun nextFreeReg() = builder.nextFreeReg()
    private fun nextFreeRegBlock(count: Int) = builder.nextFreeRegBlock(count)
    private fun markRegUsed(index: Int) = builder.markRegUsed(index)
    private fun markRegFree(index: Int) = builder.markRegFree(index)
    private fun receiverReg() = builder.receiverReg()
    private fun argReg(index: Int) = builder.argReg(index)
    private fun reg(index: Int) = builder.reg(index)

    private operator fun Opcode.unaryPlus() {
        builder.addOpcode(this)
    }

    inner class RegList(val firstReg: Int, val count: Int) {
        fun markUsed() {
            for (i in 0 until count)
                builder.markRegUsed(firstReg + i)
        }

        fun markFree() {
            for (i in 0 until count)
                builder.markRegFree(firstReg + i)
        }
    }
}
