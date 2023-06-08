package com.reevajs.reeva.ast.statements

import com.reevajs.reeva.ast.*

interface DeclarationNode : AstNode {
    val declarations: List<VariableSourceProvider>
}

interface VariableSourceProvider : AstNode {
    fun sources(): List<VariableSourceNode>

    fun names() = sources().map { it.name() }
}

sealed interface Declaration : VariableSourceProvider {
    val initializer: AstNode?
}

class LexicalDeclarationNode(
    val isConst: Boolean,
    override val declarations: List<Declaration>,
) : AstNodeBase(), DeclarationNode {
    override val children get() = declarations

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class VariableDeclarationNode(override val declarations: List<Declaration>) : AstNodeBase(), DeclarationNode {
    override val children get() = declarations

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class DestructuringDeclaration(
    val pattern: BindingPatternNode,
    override val initializer: AstNode?,
) : AstNodeBase(), Declaration {
    override val children get() = listOfNotNull(pattern, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = pattern.sources()
}

class NamedDeclaration(
    val identifier: IdentifierNode,
    override val initializer: AstNode?,
) : VariableSourceNode(), Declaration {
    override val children get() = listOfNotNull(identifier, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun name() = identifier.processedName

    override fun sources() = listOf(this)
}
