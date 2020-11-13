package me.mattco.reeva.compiler

import codes.som.anthony.koffee.MethodAssembly
import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.*
import codes.som.anthony.koffee.labels.LabelLike
import codes.som.anthony.koffee.modifiers.public
import codes.som.anthony.koffee.sugar.ClassAssemblyExtension.init
import codes.som.anthony.koffee.types.TypeLike
import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.expressions.TemplateLiteralNode
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSReference
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSFunctionProto
import me.mattco.reeva.runtime.functions.JSRuntimeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.JSObjectProto
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.TryCatchBlockNode

class Compiler {
    private var stackHeight = 0
    private val labelNodes = mutableListOf<LabelNode>()
    private val dependencies = mutableListOf<NamedByteArray>()

    data class LabelNode(
        val stackHeight: Int,
        val labelName: String,
        val breakLabel: LabelLike?,
        val continueLabel: LabelLike?
    )

    data class CompilationResult(
        val primary: NamedByteArray,
        val dependencies: List<NamedByteArray>,
    )

    data class NamedByteArray(val name: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other)
                return true
            return other is NamedByteArray && name == other.name && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    fun compileScript(script: ScriptNode): CompilationResult {
        dependencies.clear()

        val className = "TopLevelScript_${Reeva.nextId()}"
        val classNode = assembleClass(public, className, superName = "me/mattco/reeva/compiler/TopLevelScript") {
            init(public, superClass = "me/mattco/reeva/compiler/TopLevelScript") {
                _return
            }

            method(public, "run", JSValue::class) {
                currentLocalIndex++
                globalDeclarationInstantiation(script)
                compileStatementList(script.statementList)
                loadUndefined()
                areturn
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return CompilationResult(
            NamedByteArray(className, writer.toByteArray()),
            dependencies
        )
    }

    fun compileModule(module: ModuleNode): CompilationResult {
        dependencies.clear()

        val className = "TopLevelModule_${Reeva.nextId()}"
        val classNode = assembleClass(public, className, superName = "me/mattco/reeva/compiler/TopLevelScript") {
            init(public, superClass = "me/mattco/reeva/compiler/TopLevelScript") {
                _return
            }

            method(public, "run", JSValue::class) {
                currentLocalIndex++
                TODO()
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        return CompilationResult(
            NamedByteArray(className, writer.toByteArray()),
            dependencies
        )
    }

    private fun MethodAssembly.globalDeclarationInstantiation(node: ScriptNode) {
        loadRealm()
        invokevirtual(Realm::class, "getGlobalEnv", GlobalEnvRecord::class)

        val lexNames = node.lexicallyDeclaredNames()
        val varNames = node.varDeclaredNames()
        // TODO: Won't this eventually be handled by static semantics or early errors?
        lexNames.forEach {
            dup
            ldc(it)
            invokevirtual(GlobalEnvRecord::class, "hasVarDeclaration", Boolean::class, String::class)
            ifStatement(JumpCondition.True) {
                construct(Errors.TODO::class, String::class) {
                    ldc("globalDeclarationInstantiation 1")
                }
                invokevirtual(Error::class, "throwSyntaxError", Nothing::class)
                loadUndefined()
                areturn
            }
            dup
            ldc(it)
            invokevirtual(GlobalEnvRecord::class, "hasLexicalDeclaration", Boolean::class, String::class)
            ifStatement(JumpCondition.True) {
                construct(Errors.TODO::class, String::class) {
                    ldc("globalDeclarationInstantiation 2")
                }
                invokevirtual(Error::class, "throwSyntaxError", Nothing::class)
                loadUndefined()
                areturn
            }
        }
        varNames.forEach {
            dup
            ldc(it)
            invokevirtual(GlobalEnvRecord::class, "hasLexicalDeclaration", Boolean::class, String::class)
            ifStatement(JumpCondition.True) {
                construct(Errors.TODO::class, String::class) {
                    ldc("globalDeclarationInstantiation 3")
                }
                invokevirtual(Error::class, "throwSyntaxError", void)
                loadUndefined()
                areturn
            }
        }
        val varDeclarations = node.varScopedDeclarations()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        varDeclarations.asReversed().forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode) {
                val functionName = it.boundNames()[0]
                if (functionName !in declaredFunctionNames) {
                    dup
                    ldc(functionName)
                    invokevirtual(GlobalEnvRecord::class, "canDeclareGlobalFunction", Boolean::class, String::class)
                    ifStatement(JumpCondition.False) {
                        construct(Errors.TODO::class, String::class) {
                            ldc("globalDeclarationInstantiation 4")
                        }
                        invokevirtual(Error::class, "throwSyntaxError", Nothing::class)
                        loadUndefined()
                        areturn
                    }
                    declaredFunctionNames.add(functionName)
                    functionsToInitialize.add(0, it as FunctionDeclarationNode)
                }
            }
        }
        val declaredVarNames = mutableListOf<String>()
        varDeclarations.forEach {
            if (it !is VariableDeclarationNode && it !is ForBindingNode && it !is BindingIdentifierNode)
                return@forEach
            it.boundNames().forEach { name ->
                if (name !in declaredFunctionNames) {
                    dup
                    ldc(name)
                    invokevirtual(GlobalEnvRecord::class, "canDeclareGlobalVar", Boolean::class, String::class)
                    ifStatement(JumpCondition.True) {
                        construct(Errors.TODO::class, String::class) {
                            ldc("globalDeclarationInstantiation 4")
                        }
                        invokevirtual(Error::class, "throwSyntaxError", Nothing::class)
                        loadUndefined()
                        areturn
                    }
                    if (name !in declaredVarNames)
                        declaredVarNames.add(name)
                }
            }
        }
        val lexDeclarations = node.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                val isConstant = decl.isConstantDeclaration()
                ldc(isConstant)
                invokevirtual(
                    EnvRecord::class,
                    if (isConstant) "createImmutableBinding" else "createMutableBinding",
                    void,
                    String::class,
                    Boolean::class
                )
            }
        }
        functionsToInitialize.forEach { func ->
            val functionName = func.boundNames()[0]
            dup
            dup
            instantiateFunctionObject(func)
            ldc(functionName)
            swap
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalFunctionBinding", void, String::class, JSFunction::class, Boolean::class)
        }
        declaredVarNames.forEach {
            dup
            ldc(it)
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalVarBinding", void, String::class, Boolean::class)
        }

        pop
    }

    // Consumes an EnvRecord from the stack, pushes a JSFunction
    private fun MethodAssembly.instantiateFunctionObject(functionNode: FunctionDeclarationNode) {
        loadRealm()
        invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)

        ordinaryFunctionCreate(
            "TODO",
            functionNode.parameters,
            functionNode.body,
            JSFunction.ThisMode.NonLexical,
            functionNode.identifier?.identifierName ?: "<anonymous>",
        )

        if (functionNode.identifier != null) {
            dup
            construct(PropertyKey::class, String::class) {
                ldc(functionNode.identifier.identifierName)
            }
            operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
            pop
        }

        dup
        operation("makeConstructor", void, JSFunction::class)
    }

    // Consumes an EnvRecord and a JSObject (the prototype) from the stack, pushes a JSFunction
    private fun MethodAssembly.ordinaryFunctionCreate(
        sourceText: String,
        parameters: FormalParametersNode,
        body: FunctionStatementList,
        thisMode: JSFunction.ThisMode,
        name: String = "<anonymous>",
    ) {
        val functionName = "Function_${name}_${Reeva.nextId()}"
        val isStrict = body.statementList?.hasUseStrictDirective() == true
        val funcThisMode = when {
            thisMode == JSFunction.ThisMode.Lexical -> JSFunction.ThisMode.Lexical
            isStrict -> JSFunction.ThisMode.Strict
            else -> JSFunction.ThisMode.Global
        }

        val prevLocalIndex = currentLocalIndex

        val functionClassNode = assembleClass(
            public,
            functionName,
            superName = "me/mattco/reeva/runtime/functions/JSRuntimeFunction"
        ) {
            method(public, "<init>", void, EnvRecord::class, JSObject::class) {
                currentLocalIndex = 3
                aload_0
                loadRealm()

                getstatic(JSFunction.ThisMode::class, funcThisMode.name, JSFunction.ThisMode::class)

                aload_1
                ldc(isStrict)
                loadUndefined()
                ldc(sourceText)
                aload_2

                invokespecial(
                    JSRuntimeFunction::class,
                    "<init>",
                    void,
                    Realm::class,
                    JSFunction.ThisMode::class,
                    EnvRecord::class,
                    Boolean::class,
                    JSValue::class,
                    String::class,
                    JSObject::class,
                )

                _return
            }

            method(public, "evalBody", JSValue::class, List::class) {
                currentLocalIndex = 2
                aload_0
                aload_1
                functionDeclarationInstantiation(parameters, body, funcThisMode, isStrict)
                body.statementList?.also {
                    compileStatementList(it)
                }
                loadUndefined()
                areturn
            }
        }

        currentLocalIndex = prevLocalIndex

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        functionClassNode.accept(writer)
        dependencies.add(NamedByteArray(functionName, writer.toByteArray()))

        new(functionName)
        dup_x2
        dup_x2
        pop
        invokespecial(functionName, "<init>", void, EnvRecord::class, JSObject::class)

        val indexOfLastNormal = parameters.functionParameters.parameters.indexOfLast {
            it.bindingElement.binding.initializer == null
        }

        dup
        construct(JSString::class, String::class) {
            ldc("length")
        }
        construct(Descriptor::class, JSValue::class, Int::class) {
            construct(JSNumber::class, Int::class) {
                ldc(indexOfLastNormal + 1)
            }
            ldc(Descriptor.CONFIGURABLE)
        }
        operation("definePropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, Descriptor::class)
        pop
    }

    // Consumes JSFunction and List<JSValue> from the stack
    private fun MethodAssembly.functionDeclarationInstantiation(
        parameters: FormalParametersNode,
        body: FunctionStatementList,
        thisMode: JSFunction.ThisMode,
        isStrict: Boolean,
    ) {
        val arguments = astore()

        dup

        // We will need the function to create a mapped arguments object, but
        // we currently do not do that
//        val func = astore()
        pop

        invokevirtual(JSFunction::class, "isStrict", Boolean::class)

        val parameterNames = parameters.boundNames()
        val hasDuplicates = parameterNames.distinct().size != parameterNames.size
        val simpleParameterList = parameters.isSimpleParameterList()
        val hasParameterExpressions = parameters.containsExpression()
        val varNames = body.varDeclaredNames()
        val varDeclarations = body.varScopedDeclarations()
        val lexicalNames = body.lexicallyDeclaredNames()
        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        varDeclarations.asReversed().forEach { decl ->
            if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode)
                return@forEach
            expect(decl is FunctionDeclarationNode)
            val functionName = decl.boundNames()[0]
            if (functionName in functionNames)
                return@forEach
            functionNames.add(0, functionName)
            functionsToInitialize.add(0, decl)
        }

        val argumentsObjectNeeded = when {
            thisMode == JSFunction.ThisMode.Lexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexicalNames) -> false
            else -> true
        }

        loadLexicalEnv()

        if (!isStrict && hasParameterExpressions) {
            createDeclarativeEnvRecord()
            dup
            storeLexicalEnv()
        }

        parameterNames.forEach { name ->
            dup
            ldc(name)
            invokevirtual(EnvRecord::class, "hasBinding", Boolean::class, String::class)
            ifStatement(JumpCondition.False) {
                dup
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                if (hasDuplicates) {
                    dup
                    ldc(name)
                    loadUndefined()
                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        val parameterBindings = if (argumentsObjectNeeded) {
            dup
            ldc("arguments")
            ldc(false)
            if (isStrict) {
                invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
            } else {
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
            }

            dup
            ldc("arguments")
            // TODO: Figure out how to create a mapped arguments object
//            if (isStrict || !simpleParameterList) {
                load(arguments)
                operation("createUnmappedArgumentsObject", JSValue::class, List::class)
//            } else {
//
//            }
            invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            parameterNames + listOf("arguments")
        } else parameterNames

        parameters.functionParameters.parameters.forEachIndexed { index, parameter ->
            ldc(parameter.bindingElement.binding.identifier.identifierName)
            operation("resolveBinding", JSValue::class, String::class)
            ldc(index)
            load(arguments)
            invokeinterface(List::class, "size", Int::class)
            ifElseStatement(JumpCondition.GreaterThan) {
                ifBlock {
                    loadUndefined()
                }

                elseBlock {
                    load(arguments)
                    ldc(index)
                    invokeinterface(List::class, "get", Any::class)
                }
            }

            val value = astore()

            if (parameter.bindingElement.binding.initializer != null) {
                load(value)
                loadUndefined()
                ifStatement(JumpCondition.RefEqual) {
                    compileExpression(parameter.bindingElement.binding.initializer.node)
                    stackHeight--
                    getValue
                    astore(value.index)
                }
            }

            if (hasDuplicates) {
                load(value)
                operation("putValue", void, JSValue::class, JSValue::class)
            } else {
                load(value)
                operation("initializeReferencedBinding", void, JSValue::class, JSValue::class)
            }
        }

        if (parameters.restParameter != null) {
            val startingIndex = parameters.functionParameters.parameters.size
            ldc(parameters.restParameter.element.identifier.identifierName)
            operation("resolveBinding", JSValue::class, String::class)
            load(arguments)
            invokeinterface(List::class, "size", Int::class)
            dup
            ldc(startingIndex)
            ifElseStatement(JumpCondition.GreaterThanOrEqual) {
                ifBlock {
                    pop
                    ldc(0)
                    operation("arrayCreate", JSValue::class)
                }

                elseBlock {
                    ldc(startingIndex)
                    isub
                    operation("arrayCreate", JSValue::class, Int::class)
                    val arr = astore()

                    val loopStart = makeLabel()
                    val loopEnd = makeLabel()

                    ldc(startingIndex)

                    placeLabel(loopStart)

                    dup
                    ldc(arguments)
                    invokeinterface(List::class, "size", Int::class)
                    ifStatement(JumpCondition.GreaterThanOrEqual) {
                        pop
                        goto(loopEnd)
                    }

                    load(arr)

                    dup
                    new<JSNumber>()
                    dup_x1
                    swap
                    invokespecial(JSNumber::class, "<init>", Int::class)

                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)

                    ldc(1)
                    iadd

                    goto(loopStart)

                    placeLabel(loopEnd)

                    load(arr)
                }
            }

            if (hasDuplicates) {
                operation("putValue", void, JSValue::class, JSValue::class)
            } else {
                operation("initializeReferencedBinding", void, JSValue::class, JSValue::class)
            }
        }

        val varEnv: Local

        if (hasParameterExpressions) {
            dup
            val env = astore()
            createDeclarativeEnvRecord()
            dup
            varEnv = astore()
            storeVariableEnv()

            val instantiatedVarNames = mutableListOf<String>()
            varNames.forEach { name ->
                if (name in instantiatedVarNames)
                    return@forEach

                instantiatedVarNames.add(name)
                load(varEnv)
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", String::class, Boolean::class)

                load(varEnv)
                ldc(name)
                if (name !in parameterBindings || name in functionNames) {
                    loadUndefined()
                } else {
                    load(env)
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                }
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            }
        } else {
            varEnv = astore()
            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    load(varEnv)
                    dup
                    ldc(name)
                    ldc(false)
                    operation("createMutableBinding", void, String::class, Boolean::class)
                    ldc(name)
                    loadUndefined()
                    operation("initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        val lexEnv = if (isStrict) {
            varEnv
        } else {
            load(varEnv)
            createDeclarativeEnvRecord()
            astore()
        }

        load(lexEnv)
        storeLexicalEnv()

        body.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                load(lexEnv)
                ldc(name)
                if (decl.isConstantDeclaration()) {
                    ldc(true)
                    invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                } else {
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
        }

        functionsToInitialize.forEach { decl ->
            load(varEnv)
            ldc(decl.boundNames()[0])
            load(lexEnv)
            instantiateFunctionObject(decl)
            ldc(false)
            invokevirtual(EnvRecord::class, "setMutableBinding", void, String::class, JSValue::class, Boolean::class)
        }
    }

    private fun MethodAssembly.compileStatementList(statementListNode: StatementListNode) {
        // TODO: store last value
        statementListNode.statements.forEach {
            compileStatement(it as StatementNode)
        }
    }

    private fun MethodAssembly.compileStatement(statement: StatementNode) {
        when (statement) {
            is BlockStatementNode -> compileBlockStatement(statement)
            is VariableStatementNode -> compileVariableStatement(statement)
            is EmptyStatementNode -> JSEmpty
            is ExpressionStatementNode -> compileExpressionStatement(statement)
            is IfStatementNode -> compileIfStatement(statement)
//            is BreakableStatement -> compileBreakableStatement(statement)
            is IterationStatement -> compileIterationStatement(statement, null)
            is LabelledStatementNode -> compileLabelledStatement(statement)
            is LexicalDeclarationNode -> compileLexicalDeclaration(statement)
            is FunctionDeclarationNode -> compileFunctionDeclaration(statement)
            is ClassDeclarationNode -> compileClassDeclaration(statement)
            is ReturnStatementNode -> compileReturnStatement(statement)
            is ThrowStatementNode -> compileThrowStatement(statement)
            is TryStatementNode -> compileTryStatement(statement)
            is BreakStatementNode -> compileBreakStatement(statement)
            is ImportDeclarationNode -> compileImportDeclaration(statement)
            is ExportDeclarationNode -> compileExportDeclaration(statement)
            else -> TODO()
        }
    }

    private fun MethodAssembly.compileBlockStatement(node: BlockStatementNode) {
        compileBlock(node.block)
    }

    private fun MethodAssembly.compileBlock(node: BlockNode) {
        if (node.statements == null)
            return
        loadLexicalEnv()
        dup
        createDeclarativeEnvRecord()
        // oldEnv, blockEnv

        // BlockDeclarationInstantiation start
        node.statements.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                // oldEnv, blockEnv, blockEnv, name
                val isConstant = decl.isConstantDeclaration()
                ldc(isConstant)
                // oldEnv, blockEnv, blockEnv, name, boolean
                invokevirtual(
                    EnvRecord::class,
                    if (isConstant) "createImmutableBinding" else "createMutableBinding",
                    void,
                    String::class,
                    Boolean::class
                )
                // oldEnv, blockEnv
            }
            if (decl is FunctionDeclarationNode) {
                dup
                dup
                instantiateFunctionObject(decl)
                ldc(decl.boundNames()[0])
                swap
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            }
        }
        // BlockDeclarationInstantiation end

        // oldEnv, blockEnv
        loadContext()
        // oldEnv, blockEnv, context
        swap
        // oldEnv, context, blockEnv
        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        // oldEnv
        val oldEnv = astore()

        tryCatchBuilder {
            tryBlock {
                compileStatementList(node.statements)
            }

            finallyBlock {
                loadContext()
                load(oldEnv)
                putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
            }
        }
    }

    private fun MethodAssembly.compileVariableStatement(node: VariableStatementNode) {
        node.declarations.declarations.forEach { decl ->
            if (decl.initializer == null)
                return@forEach
            ldc(decl.identifier.identifierName)
            operation("resolveBinding", JSReference::class, String::class)
            stackHeight++
            compileExpression(decl.initializer.node)
            getValue
            // lhs, rhs
            if (Operations.isAnonymousFunctionDefinition(decl.initializer.node)) {
                dup
                construct(PropertyKey::class, String::class) {
                    ldc(decl.identifier.identifierName)
                }
                operation("setFunctionName", Boolean::class, JSValue::class, PropertyKey::class)
                pop
            }
            operation("putValue", void, JSValue::class, JSValue::class)
            stackHeight -= 2
        }
    }

    private fun MethodAssembly.compileExpressionStatement(node: ExpressionStatementNode) {
        compileExpression(node.node)
        getValue
        pop
        stackHeight--
    }

    private fun MethodAssembly.compileIfStatement(node: IfStatementNode) {
        compileExpression(node.condition)
        getValue
        toBoolean
        stackHeight--

        if (node.falseBlock != null) {
            ifElseStatement(JumpCondition.True) {
                ifBlock {
                    compileStatement(node.trueBlock)
                }

                elseBlock {
                    compileStatement(node.falseBlock)
                }
            }
        } else {
            ifStatement(JumpCondition.True) {
                compileStatement(node.trueBlock)
            }
        }
    }

//    fun MethodAssembly.compileBreakableStatement(node: BreakableStatement) {
//        labelledEvaluation(node, emptySet())
//    }

    private fun MethodAssembly.compileIterationStatement(node: IterationStatement, label: String?) {
        when (node) {
            is DoWhileStatementNode -> compileDoWhileStatement(node, label)
            is WhileStatementNode -> compileWhileStatement(node, label)
            is ForStatementNode -> compileForStatement(node, label)
//            is ForInNode -> compileForInNode(node, label)
//            is ForOfNode -> compileForOfNode(node, label)
        }
    }

    private fun MethodAssembly.compileDoWhileStatement(node: DoWhileStatementNode, label: String?) {
        val start = makeLabel()
        val end = makeLabel()
        if (label != null)
            labelNodes.add(LabelNode(stackHeight, label, end, start))

        placeLabel(start)
        compileStatement(node.body)
        compileExpression(node.condition)
        getValue
        toBoolean
        stackHeight--
        ifStatement(JumpCondition.False) {
            goto(start)
        }

        placeLabel(end)
    }

    private fun MethodAssembly.compileWhileStatement(node: WhileStatementNode, label: String?) {
        val start = makeLabel()
        val end = makeLabel()
        if (label != null)
            labelNodes.add(LabelNode(stackHeight, label, end, start))

        placeLabel(start)
        compileExpression(node.condition)
        stackHeight--
        ifStatement(JumpCondition.True) {
            compileStatement(node.body)
            goto(start)
        }

        placeLabel(end)
    }

    private fun MethodAssembly.compileForStatement(node: ForStatementNode, label: String?) {
        when (node.initializer) {
            is ExpressionNode -> {
                compileExpression(node.initializer)
                getValue
                pop
                stackHeight--

                forBodyEvaluation(
                    node.condition,
                    node.incrementer,
                    node.body,
                    emptyList(),
                    label,
                )
            }
            is VariableStatementNode -> {
                compileVariableStatement(node.initializer)

                forBodyEvaluation(
                    node.condition,
                    node.incrementer,
                    node.body,
                    emptyList(),
                    label,
                )
            }
            is LexicalDeclarationNode -> {
                val isConst = node.initializer.isConstantDeclaration()
                val boundNames = node.initializer.boundNames()

                loadLexicalEnv()
                dup
                // oldEnv, oldEnv
                createDeclarativeEnvRecord()
                // oldEnv, loopEnv

                boundNames.forEach {
                    dup
                    ldc(it)
                    ldc(isConst)
                    // oldEnv, loopEnv, loopEnv, string, boolean
                    invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                    // oldEnv, loopEnv
                }

                storeLexicalEnv()
                val oldEnv = astore()

                tryCatchBuilder {
                    tryBlock {
                        compileLexicalDeclaration(node.initializer)
                        forBodyEvaluation(
                            node.condition,
                            node.incrementer,
                            node.body,
                            if (isConst) emptyList() else boundNames,
                            label,
                        )
                    }

                    finallyBlock {
                        load(oldEnv)
                        storeLexicalEnv()
                    }
                }
            }
        }
    }

    private fun MethodAssembly.forBodyEvaluation(
        condition: ExpressionNode?,
        incrementer: ExpressionNode?,
        body: StatementNode,
        perIterationBindings: List<String>,
        label: String?,
    ) {
        val start = makeLabel()
        val end = makeLabel()
        if (label != null)
            labelNodes.add(LabelNode(stackHeight, label, end, start))

        createPerIterationEnvironment(perIterationBindings)
        placeLabel(start)

        if (condition != null) {
            compileExpression(condition)
            getValue
            toBoolean
            stackHeight--
            ifStatement(JumpCondition.False) {
                goto(end)
            }
        }

        compileStatement(body)
        createPerIterationEnvironment(perIterationBindings)

        if (incrementer != null) {
            compileExpression(incrementer)
            getValue
            pop
            stackHeight--
        }

        goto(start)

        placeLabel(end)
    }

    private fun MethodAssembly.createPerIterationEnvironment(perIterationBindings: List<String>) {
        if (perIterationBindings.isEmpty())
            return

        loadLexicalEnv()
        dup
        // lastIterationEnv, lastIterationEnv
        getfield(EnvRecord::class, "outerEnv", EnvRecord::class)
        // lastIterationEnv, outer
        createDeclarativeEnvRecord()
        // lastIterationEnv, thisIterationEnv

        val thisIterationEnv = astore()
        val lastIterationEnv = astore()

        tryCatchBuilder {
            tryBlock {
                perIterationBindings.forEach {
                    load(thisIterationEnv)
                    ldc(it)
                    ldc(false)
                    // string, false, env
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)

                    load(thisIterationEnv)
                    ldc(it)
                    // thisIterationEnv, string

                    load(lastIterationEnv)
                    ldc(it)
                    ldc(true)
                    // thisIterationEnv, string, binding, true, env
                    invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                    // thisIterationEnv, string, value

                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }

            finallyBlock {
                load(thisIterationEnv)
                storeLexicalEnv()
            }
        }
    }

    private fun MethodAssembly.compileLexicalDeclaration(node: LexicalDeclarationNode) {
        node.bindingList.lexicalBindings.forEach { binding ->
            ldc(binding.identifier.identifierName)
            operation("resolveBinding", JSReference::class, String::class)
            stackHeight++
            if (binding.initializer == null) {
                expect(!node.isConst)
                loadUndefined()
            } else {
                compileExpression(binding.initializer.node)
                stackHeight--
                if (Operations.isAnonymousFunctionDefinition(binding.initializer.node)) {
                    dup
                    checkcast<JSFunction>()
                    ldc(binding.identifier.identifierName)
                    operation("setFunctionName", Boolean::class, JSFunction::class, String::class)
                    pop
                }
                getValue
            }
            operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
            stackHeight--
        }
    }

    private fun MethodAssembly.compileFunctionDeclaration(node: StatementNode) {
        // nop
    }

    private fun MethodAssembly.compileClassDeclaration(node: ClassDeclarationNode) {
        bindingClassDeclarationEvaluation(node)
        pop
    }

    private fun MethodAssembly.bindingClassDeclarationEvaluation(classDeclarationNode: ClassDeclarationNode) {
        val node = classDeclarationNode.classNode
        if (node.identifier == null) {
            classDefinitionEvaluation(node, null, "default")
        } else {
            val className = node.identifier.identifierName
            classDefinitionEvaluation(node, className, className)
            dup
            loadLexicalEnv()
            initializeBoundName(className)
        }
    }

    private fun MethodAssembly.classDefinitionEvaluation(node: ClassNode, classBinding: String?, className: String) {
        loadLexicalEnv()
        dup
        val env = astore()
        createDeclarativeEnvRecord()
        val classScope = astore()

        if (classBinding != null) {
            load(classScope)
            ldc(classBinding)
            ldc(true)
            invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
        }

        // Push protoParent and constructorParent onto the stack
        if (node.heritage == null) {
            loadRealm()
            dup
            invokevirtual(Realm::class, "getObjectProto", JSObjectProto::class)
            invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
        } else {
            load(classScope)
            storeLexicalEnv()
            compileExpression(node.heritage)
            load(env)
            storeLexicalEnv()
            getValue
            dup
            val superclass = astore()

            loadNull()
            ifElseStatement(JumpCondition.RefEqual) {
                ifBlock {
                    loadNull()
                    loadRealm()
                    invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
                }

                elseBlock {
                    load(superclass)
                    operation("isConstructor", Boolean::class, JSValue::class)

                    ifElseStatement(JumpCondition.True) {
                        ifBlock {
                            load(superclass)
                            checkcast<JSObject>()
                            ldc("prototype")
                            invokevirtual(JSObject::class, "get", JSValue::class, String::class)

                            val end = makeLabel()

                            dup
                            instanceof<JSObject>()
                            ifStatement(JumpCondition.True) {
                                goto(end)
                            }

                            dup
                            loadNull()
                            ifStatement(JumpCondition.RefNotEqual) {
                                goto(end)
                            }

                            loadKObject<Errors.Class.BadExtendsProto>()
                            invokevirtual(Error::class, "throwTypeError", Nothing::class)

                            placeLabel(end)

                            load(superclass)
                        }

                        elseBlock {
                            loadKObject<Errors.Class.BadExtends>()
                            invokevirtual(Error::class, "throwTypeError", Nothing::class)
                        }
                    }
                }
            }
        }

        // protoParent, ctorParent
        swap
        loadRealm()
        // ctorParent, protoParent, realm
        swap
        // ctorParent, realm, protoParent

        invokestatic(JSObject::class, "create", JSObject::class, Realm::class, JSObject::class)
        val proto = astore()

        val constructor = (node.body.constructorMethod() as? ClassElementNode)?.node as? MethodDefinitionNode ?:
        if (node.heritage != null) {
            MethodDefinitionNode(
                PropertyNameNode(IdentifierNode("constructor"), false),
                FormalParametersNode(
                    FormalParameterListNode(emptyList()),
                    FormalRestParameterNode(
                        BindingRestElementNode(BindingIdentifierNode("args"))
                    )
                ),
                FunctionStatementList(
                    StatementListNode(listOf(ExpressionStatementNode(
                        SuperCallNode(ArgumentsNode(ArgumentsListNode(listOf(
                            ArgumentListEntry(
                                IdentifierReferenceNode("args"),
                                true
                            )
                        ))))
                    )))
                ),
                MethodDefinitionNode.Type.Normal
            )
        } else {
            MethodDefinitionNode(
                PropertyNameNode(IdentifierNode("constructor"), false),
                FormalParametersNode(FormalParameterListNode(emptyList()), null),
                FunctionStatementList(null),
                MethodDefinitionNode.Type.Normal
            )
        }

        load(classScope)
        storeLexicalEnv()

        defineMethod(constructor)

        // DefinedMethod
        invokevirtual(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)

        // closure
        dup
        val classFunction = astore()

        // closure
        dup
        ldc(true)
        invokevirtual(JSFunction::class, "setStrict", void, Boolean::class)

        dup
        construct(PropertyKey::class, String::class) {
            ldc(className)
        }
        operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
        pop

        dup
        ldc(false)
        load(proto)
        operation("makeConstructor", void, JSFunction::class, Boolean::class, JSObject::class)

        if (node.heritage != null) {
            dup
            getfield(JSFunction.ConstructorKind::class, "Derived", JSFunction.ConstructorKind::class)
            invokevirtual(JSFunction::class, "setConstructorKind", void, JSFunction.ConstructorKind::class)
        }

        dup
        ldc(true)
        invokevirtual(JSFunction::class, "setClassConstructor", void, Boolean::class)

        new<Descriptor>()
        dup_x1
        swap
        ldc(Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        invokespecial(Descriptor::class, "<init>", void, JSValue::class, Int::class)

        load(proto)
        swap
        construct(PropertyKey::class, String::class) {
            ldc("constructor")
        }
        swap
        invokevirtual(JSObject::class, "defineOwnProperty", Boolean::class, PropertyKey::class, Descriptor::class)
        pop

        construct(ArrayList::class)

        node.body.elements.filter {
            it.node != constructor
        }.forEach { element ->
            dup
            // instanceFields, instanceFields
            tryCatchBuilder {
                tryBlock {
                    if (element.isStatic) {
                        load(classFunction)
                    } else {
                        load(proto)
                    }
                    classElementEvaluation(element, false, element.isStatic)
                    dup
                    ifElseStatement(JumpCondition.NonNull) {
                        ifBlock {
                            invokeinterface(List::class, "add", Boolean::class, Object::class)
                            pop
                        }

                        elseBlock {
                            pop
                        }
                    }
                }

                catchBlock<ThrowException> {
                    load(env)
                    storeLexicalEnv()
                    athrow
                }
            }
        }

        load(env)
        storeLexicalEnv()
        if (classBinding != null) {
            load(classScope)
            ldc(classBinding)
            load(classFunction)
            invokevirtual(EnvRecord::class, "initializeBinding", String::class, JSFunction::class)
        }

        load(classFunction)
        swap
        invokevirtual(JSFunction::class, "setFields", void, List::class)

        load(classFunction)
    }

    // Consumes a JSObject from the stack
    private fun MethodAssembly.classElementEvaluation(element: ClassElementNode, enumerable: Boolean, isStatic: Boolean) {
        when (element.type) {
            ClassElementNode.Type.Method -> {
                ldc(true)
                propertyDefinitionEvaluation(element.node!! as MethodDefinitionNode, enumerable)
                aconst_null
            }
            ClassElementNode.Type.Field -> {
                evaluatePropertyName(element.node!!)

                if (isStatic) {
                    // obj, name
                    if (element.initializer == null) {
                        loadUndefined()
                        // obj, name, undefined
                        // <empty>
                        operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    } else {
                        compileExpression(element.initializer.node)
                        // obj, name, expr
                        getValue
                        operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                        // <empty>
                    }
                    aconst_null
                } else {
                    // obj, name
                    swap
                    // name, obj

                    if (element.initializer != null) {
                        val obj = astore()
                        new<JSFunction.FieldRecord>()
                        swap
                        loadLexicalEnv()
                        loadRealm()
                        invokevirtual(Realm::class, "getFunctionProto", JSFunctionProto::class)
                        val formalParameterList = FormalParametersNode(FormalParameterListNode(emptyList()), null)
                        // FieldRecord, name, env, funcProto
                        ordinaryFunctionCreate(
                            "TODO",
                            formalParameterList,
                            FunctionStatementList(StatementListNode(listOf(
                                ReturnStatementNode(element.initializer.node)
                            ))),
                            JSFunction.ThisMode.Lexical,
                        )
                        // FieldRecord, name, initializer
                        dup
                        ldc(true)
                        // FieldRecord, name, initializer, initializer, boolean
                        invokevirtual(JSFunction::class, "setStrict", void, Boolean::class)
                        // FieldRecord, name, initializer
                        dup
                        load(obj)
                        // FieldRecord, name, initializer, initializer, obj
                        operation("makeMethod", JSValue::class, JSFunction::class, JSObject::class)
                        // FieldRecord, name, initializer, result
                        pop
                        // FieldRecord, name, initializer
                        ldc(Operations.isAnonymousFunctionDefinition(element.initializer))
                        // FieldRecord, name, initializer, boolean
                    } else {
                        pop
                        new<JSFunction.FieldRecord>()
                        swap
                        loadKObject<JSEmpty>()
                        ldc(false)
                        // FieldRecord, name, JSEmpty, boolean
                    }

                    invokespecial(JSFunction.FieldRecord::class, "<init>", void, JSValue::class, JSValue::class, Boolean::class)
                }
            }
            ClassElementNode.Type.Empty -> aconst_null
        }
    }

    // Consumes an env and JSValue from the stack
    private fun MethodAssembly.initializeBoundName(name: String) {
        swap
        dup
        ifElseStatement(JumpCondition.Null) {
            ifBlock {
                pop
                ldc(name)
                operation("resolveBinding", JSValue::class, String::class)
                swap
                operation("putValue", void, JSValue::class, JSValue::class)
            }

            elseBlock {
                swap
                ldc(name)
                swap
                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
            }
        }
    }

    private fun MethodAssembly.compileReturnStatement(node: ReturnStatementNode) {
        if (node.node != null) {
            compileExpression(node.node)
        } else {
            loadUndefined()
        }
        areturn
    }

    private fun MethodAssembly.compileThrowStatement(node: ThrowStatementNode) {
        construct(ThrowException::class, JSValue::class) {
            compileExpression(node.expr)
            getValue
        }
        athrow
        stackHeight--
    }

    private fun MethodAssembly.compileTryStatement(node: TryStatementNode) {
        tryCatchBuilder {
            tryBlock {
                compileBlock(node.tryBlock)
            }

            if (node.catchNode != null) {
                catchBlock<ThrowException> {
                    val ex = astore()
                    if (node.catchNode.catchParameter == null) {
                        compileBlock(node.catchNode.block)
                    } else {
                        loadLexicalEnv()
                        dup
                        createDeclarativeEnvRecord()

                        val parameter = node.catchNode.catchParameter
                        parameter.boundNames().forEach { name ->
                            dup
                            ldc(name)
                            ldc(false)
                            invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                        }

                        loadContext()
                        swap
                        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)

                        tryCatchBuilder {
                            tryBlock {
                                loadLexicalEnv()
                                ldc(parameter.identifierName)
                                load(ex)
                                invokevirtual(ThrowException::class, "getValue", JSValue::class)
                                invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                                compileBlock(node.catchNode.block)
                            }

                            finallyBlock {
                                loadContext()
                                swap
                                putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
                            }
                        }
                    }
                }
            }

            if (node.finallyBlock != null)
                compileBlock(node.finallyBlock)
        }
    }

    private fun MethodAssembly.compileBreakStatement(node: BreakStatementNode) {
        var labelNode = labelNodes.removeLast()
        if (node.label != null) {
            while (labelNode.labelName != node.label.identifierName)
                labelNode = labelNodes.removeLast()
            expect(labelNode.breakLabel != null)
        } else {
            while (labelNode.breakLabel == null)
                labelNode = labelNodes.removeLast()
        }

        repeat(stackHeight - labelNode.stackHeight) {
            pop
            stackHeight--
        }

        goto(labelNode.breakLabel!!)
    }

    private fun MethodAssembly.compileLabelledStatement(node: LabelledStatementNode) {
        val label = node.label.identifierName
        when (node.item) {
            is DoWhileStatementNode -> compileDoWhileStatement(node.item, label)
            is WhileStatementNode -> compileWhileStatement(node.item, label)
            is ForStatementNode -> compileForStatement(node.item, label)
            else -> compileStatement(node.item)
        }
    }

    private fun MethodAssembly.compileImportDeclaration(node: StatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileExportDeclaration(node: StatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileExpression(node: ExpressionNode) {
        when (node) {
            ThisNode -> compileThis()
            is CommaExpressionNode -> compileCommaExpressionNode(node)
            is IdentifierReferenceNode -> compileIdentifierReference(node)
            is FunctionExpressionNode -> compileFunctionExpression(node)
            is ArrowFunctionNode -> compileArrowFunction(node)
            is LiteralNode -> compileLiteral(node)
            is NewExpressionNode -> compileNewExpression(node)
            is CallExpressionNode -> compileCallExpression(node)
            is ObjectLiteralNode -> compileObjectLiteral(node)
            is ArrayLiteralNode -> compileArrayLiteral(node)
            is MemberExpressionNode -> compileMemberExpression(node)
            is OptionalExpressionNode -> compileOptionalExpression(node)
            is AssignmentExpressionNode -> compileAssignmentExpression(node)
            is ConditionalExpressionNode -> compileConditionalExpression(node)
            is CoalesceExpressionNode -> compileCoalesceExpression(node)
            is LogicalORExpressionNode -> compileLogicalORExpression(node)
            is LogicalANDExpressionNode -> compileLogicalANDExpression(node)
            is BitwiseORExpressionNode -> compileBitwiseORExpression(node)
            is BitwiseXORExpressionNode -> compileBitwiseXORExpression(node)
            is BitwiseANDExpressionNode -> compileBitwiseANDExpression(node)
            is EqualityExpressionNode -> compileEqualityExpression(node)
            is RelationalExpressionNode -> compileRelationalExpression(node)
            is ShiftExpressionNode -> compileShiftExpression(node)
            is AdditiveExpressionNode -> compileAdditiveExpression(node)
            is MultiplicativeExpressionNode -> compileMultiplicationExpression(node)
            is ExponentiationExpressionNode -> compileExponentiationExpression(node)
            is UnaryExpressionNode -> compileUnaryExpression(node)
            is UpdateExpressionNode -> compileUpdateExpression(node)
            is ParenthesizedExpressionNode -> compileExpression(node.target)
            is ForBindingNode -> compileForBinding(node)
            is TemplateLiteralNode -> compileTemplateLiteral(node)
            is ClassExpressionNode -> compileClassExpression(node)
            is SuperPropertyNode -> compileSuperProperty(node)
            is SuperCallNode -> compileSuperCall(node)
            else -> unreachable()
        }
    }

    private fun MethodAssembly.compileThis() {
        operation("resolveThisBinding", JSValue::class)
        stackHeight++
    }

    private fun MethodAssembly.compileCommaExpressionNode(node: CommaExpressionNode) {
        node.expressions.forEachIndexed { index, expression ->
            compileExpression(expression)
            if (index != node.expressions.lastIndex) {
                pop
                stackHeight--
            }
        }
    }

    private fun MethodAssembly.compileIdentifierReference(node: IdentifierReferenceNode) {
        ldc(node.identifierName)
        operation("resolveBinding", JSReference::class, String::class)
        stackHeight++
    }

    private fun MethodAssembly.compileFunctionExpression(node: FunctionExpressionNode) {
        if (node.identifier == null) {
            loadRealm()
            getfield(Realm::class, "functionProto", JSFunctionProto::class)
            loadLexicalEnv()
            val sourceText = "TODO"

            ordinaryFunctionCreate(
                sourceText,
                node.parameters,
                node.body,
                JSFunction.ThisMode.NonLexical,
            )

            dup
            construct(PropertyKey::class, String::class) {
                ldc("")
            }
            operation("setFunctionName", JSFunction::class, PropertyKey::class)

            dup
            operation("makeConstructor", void, JSFunction::class)
        } else {
            loadLexicalEnv()
            createDeclarativeEnvRecord()
            dup
            // funcEnv, funcEnv
            ldc(node.identifier.identifierName)
            ldc(false)
            // funcEnv, funcEnv, string, boolean
            invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
            // funcEnv

            dup
            // funcEnv, funcEnv

            loadRealm()
            getfield(Realm::class, "functionProto", JSFunctionProto::class)
            val sourceText = "TODO"
            // funcEnv, funcEnv, proto

            ordinaryFunctionCreate(
                sourceText,
                node.parameters,
                node.body,
                JSFunction.ThisMode.NonLexical,
            )

            // funcEnv, closure

            dup
            construct(PropertyKey::class, String::class) {
                ldc(node.name)
            }
            operation("setFunctionName", JSFunction::class, PropertyKey::class)

            // funcEnv, closure

            dup
            operation("makeConstructor", void, JSFunction::class)

            // funcEnv, closure
            dup_x1
            // closure, funcEnv, closure
            ldc(node.identifier.identifierName)
            swap

            // closure, funcEnv, name, closure
            invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSFunction::class)
        }

        stackHeight++
    }

    private fun MethodAssembly.compileArrowFunction(node: ArrowFunctionNode) {
        loadLexicalEnv()
        val sourceText = "TODO"
        val parameters = node.parameters.let {
            if (it is BindingIdentifierNode) {
                FormalParametersNode(
                    FormalParameterListNode(
                        listOf(FormalParameterNode(BindingElementNode(SingleNameBindingNode(it, null))))
                    ),
                    null
                )
            } else it as FormalParametersNode
        }
        val body = node.body.let {
            if (it is ExpressionNode) {
                FunctionStatementList(StatementListNode(listOf(
                    ReturnStatementNode(it)
                )))
            } else it as FunctionStatementList
        }

        loadRealm()
        getfield(Realm::class, "functionProto", JSFunctionProto::class)

        ordinaryFunctionCreate(
            sourceText,
            parameters,
            body,
            JSFunction.ThisMode.Lexical,
        )

        dup
        construct(PropertyKey::class, String::class) {
            ldc(node.name)
        }
        operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
        pop

        stackHeight++
    }

    private fun MethodAssembly.compileLiteral(node: LiteralNode) {
        when (node) {
            is NullNode -> loadNull()
            is BooleanNode -> (if (node.value) JSTrue::class else JSFalse::class).let {
                getstatic(it, "INSTANCE", it)
            }
            is NumericLiteralNode -> construct(JSNumber::class, Double::class) {
                ldc(node.value)
            }
            is StringLiteralNode -> construct(JSString::class, String::class) {
                ldc(node.value)
            }
            else -> unreachable()
        }
        stackHeight++
    }

    private fun MethodAssembly.compileNewExpression(node: NewExpressionNode) {
        compileExpression(node.target)
        getValue
        dup
        operation("isConstructor", Boolean::class, JSValue::class)
        ifStatement(JumpCondition.False) {
            new<Errors.NotACtor>()
            dup_x1
            swap
            operation("toPrintableString", String::class, JSValue::class)
            invokespecial(Errors.NotACtor::class, "<init>", void, String::class)
            invokevirtual(Error::class, "throwTypeError", Nothing::class)
            loadUndefined()
            areturn
        }
        if (node.arguments == null) {
            construct(ArrayList::class)
        } else {
            argumentsListEvaluation(node.arguments)
        }
        operation("construct", JSValue::class, JSValue::class, List::class)
        stackHeight++
    }

    private fun MethodAssembly.argumentsListEvaluation(node: ArgumentsNode) {
        construct(ArrayList::class)
        stackHeight++

        val entries = node.arguments
        if (entries.isEmpty())
            return

        entries.forEach {
            dup

            if (it.isSpread) {
                // list

                compileExpression(it.expression)
                getValue
                // list, value
                operation("getIterator", Operations.IteratorRecord::class, JSValue::class)
                // list, record

                val whileStart = makeLabel()
                val whileEnd = makeLabel()

                placeLabel(whileStart)

                // list, record
                operation("iteratorStep", JSValue::class, Operations.IteratorRecord::class)
                // list, next
                dup
                // list, next, next
                loadFalse()
                // list, next, next, false
                ifElseStatement(JumpCondition.RefEqual) {
                    ifBlock {
                        // list, next
                        pop2
                        goto(whileEnd)
                    }

                    elseBlock {
                        // list, next
                        operation("iteratorValue", JSValue::class, JSValue::class)
                        // list, nextArg
                        invokeinterface(List::class, "add", Boolean::class, Object::class)
                        pop
                    }
                }

                placeLabel(whileEnd)
            } else {
                compileExpression(it.expression)
                getValue
                invokeinterface(List::class, "add", Boolean::class, Object::class)
                pop
                stackHeight--
            }
        }

    }

    private fun MethodAssembly.compileCallExpression(node: CallExpressionNode) {
        compileExpression(node.target)
        dup
        getValue
        swap
        argumentsListEvaluation(node.arguments)
        ldc(false)
        operation("evaluateCall", JSValue::class, JSValue::class, JSValue::class, List::class, Boolean::class)
        stackHeight--

        // TODO: The following code checks for a direct eval invocation, but we should
        // be able to do that statically
//        compileExpression(node.target)
//        dup
//        val ref = astore()
//        getValue
//        val func = astore()
//        argumentsListEvaluation(node.arguments)
//        val args = astore()
//
//        val end = makeLabel()
//
//        load(ref)
//        instanceof<JSReference>()
//        ifStatement(JumpCondition.False) {
//            goto(end)
//        }
//
//        load(ref)
//        checkcast<JSReference>()
//        invokevirtual(JSReference::class, "isPropertyReference", Boolean::class)
//        ifStatement(JumpCondition.True) {
//            goto(end)
//        }
//
//        load(ref)
//        checkcast<JSReference>()
//        getfield(JSReference::class, "name", PropertyKey::class)
//        dup
//        invokevirtual(PropertyKey::class, "isString", Boolean::class)
//        ifStatement(JumpCondition.False) {
//            goto(end)
//        }
//
//
//        invokevirtual(PropertyKey::class, "getAsString", String::class)
//        ldc("eval")
//        ifStatement(JumpCondition.Equal) {
//            goto(end)
//        }
//
//        load(func)
//        loadRealm()
//        invokevirtual(Realm::class, "getGlobalObject", JSObject::class)
//        ldc("eval")
//        invokevirtual(JSObject::class, "get", JSValue::class, String::class)
//        invokevirtual(JSValue::class, "sameValue", Boolean::class, JSValue::class)
//        ifStatement(JumpCondition.False) {
//            goto(end)
//        }
//
//        val pastEnd = makeLabel()
//
//        load(args)
//        invokevirtual(List::class, "isEmpty", Boolean::class)
//        ifElseStatement(JumpCondition.True) {
//            ifBlock {
//                loadUndefined()
//                goto(pastEnd)
//            }
//
//            elseBlock {
//                load(args)
//                ldc(false
//                invokevirtual(List::class, "get", JSValue::class, Int::class)
//                loadRealm()
//                operation("isStrict", Boolean::class)
//                ldc(true)
//                invokestatic(JSGlobalObject::class, "performEval", JSValue::class, JSValue::class, Realm::class, Boolean::class, Boolean::class)
//
//                goto(pastEnd)
//            }
//        }
//
//        placeLabel(end)
//        load(func)
//        load(ref)
//        load(args)
//        ldc(false
//        operation("evaluateCall", JSValue::class, JSValue::class, JSValue::class, List::class, Boolean::class)
//
//        placeLabel(pastEnd)
//
//        stackHeight++
    }

    private fun MethodAssembly.compileObjectLiteral(node: ObjectLiteralNode) {
        loadRealm()
        invokestatic(JSObject::class, "create", JSObject::class, Realm::class)

        stackHeight++

        if (node.list == null)
            return

        node.list.properties.forEach { property ->
            dup
            when (property.type) {
                PropertyDefinitionNode.Type.KeyValue -> {
                    evaluatePropertyName(property.first)
                    compileExpression(property.second!!)
                    getValue
                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    pop
                    stackHeight--
                }
                PropertyDefinitionNode.Type.Shorthand -> {
                    expect(property.first is IdentifierReferenceNode)
                    construct(JSString::class, String::class) {
                        ldc(property.first.identifierName)
                    }
                    compileIdentifierReference(property.first)
                    getValue
                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    pop
                    stackHeight--
                }
                PropertyDefinitionNode.Type.Method -> {
                    val method = property.first as MethodDefinitionNode

                    propertyDefinitionEvaluation(method, true)
                }
                PropertyDefinitionNode.Type.Spread -> TODO()
            }
        }
    }

    // Takes obj (JSObject) and isStrict (Boolean) on the stack. Does not push a result
    private fun MethodAssembly.propertyDefinitionEvaluation(
        methodDefinitionNode: MethodDefinitionNode,
        enumerable: Boolean,
    ) {
        // obj, isStrict
        val enumAttr = if (enumerable) Descriptor.ENUMERABLE else 0

        when (methodDefinitionNode.type) {
            MethodDefinitionNode.Type.Normal -> {
                defineMethod(methodDefinitionNode)
                // obj, isStrict, DefinedMethod
                swap
                ifStatement(JumpCondition.True) {
                    // obj, DefinedMethod
                    dup
                    getfield(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)
                    invokevirtual(JSFunction::class, "setIsStrict", void, Boolean::class)
                }
                // obj, DefinedMethod
                dup
                getfield(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)
                // obj, DefinedMethod, closure
                swap
                // obj, closure, DefinedMethod
                dup_x1
                // obj, DefinedMethod, closure, DefinedMethod
                getfield(Interpreter.DefinedMethod::class, "getKey", PropertyKey::class)
                // obj, DefinedMethod, closure, PropertyKey
                operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                pop
                // obj, DefinedMethod
                dup
                // obj, DefinedMethod, DefinedMethod
                getfield(Interpreter.DefinedMethod::class, "getKey", PropertyKey::class)
                // obj, DefinedMethod, key
                swap
                // obj, key, DefinedMethod
                getfield(Interpreter.DefinedMethod::class, "getClosure", JSFunction::class)
                // obj, key, closure

                new<Descriptor>()
                // obj, key, closure, Descriptor
                dup_x1
                // obj, key, Descriptor, closure, Descriptor
                swap
                // obj, key, Descriptor, Descriptor, closure
                ldc(Descriptor.CONFIGURABLE or enumAttr or Descriptor.WRITABLE)
                // obj, key, Descriptor, Descriptor, closure, attrs
                invokespecial(Descriptor::class, "<init>", void, JSValue::class, Int::class)
                // obj, key, Descriptor
                operation("definePropertyOrThrow", Boolean::class, JSValue::class, PropertyKey::class, Descriptor::class)
                pop
            }
            MethodDefinitionNode.Type.Getter -> TODO()
            MethodDefinitionNode.Type.Setter -> TODO()
            MethodDefinitionNode.Type.Generator -> TODO()
            MethodDefinitionNode.Type.Async -> TODO()
            MethodDefinitionNode.Type.AsyncGenerator -> TODO()
        }
    }

    // Takes obj (JSObject) and functionPrototype (JSObject) on the stack, pushes DefinedMethod
    private fun MethodAssembly.defineMethod(method: MethodDefinitionNode) {
        expect(method.type == MethodDefinitionNode.Type.Normal)

        loadLexicalEnv()
        ordinaryFunctionCreate(
            "TODO",
            method.parameters,
            method.body,
            JSFunction.ThisMode.NonLexical,
        )
        // obj, closure
        dup_x1
        // closure, obj, functionPrototype
        operation("makeMethod", JSValue::class, JSFunction::class, JSObject::class)
        pop

        // closure
        new<Interpreter.DefinedMethod>()
        // closure, DefinedMethod
        dup_x1
        swap
        // DefinedMethod, DefinedMethod, closure

        evaluatePropertyName(method.identifier)
        swap

        // DefinedMethod, DefinedMethod, key, closure
        invokespecial(Interpreter.DefinedMethod::class, "<init>", void, PropertyKey::class, JSFunction::class)
        // DefinedMethod

        stackHeight -= 2
    }

    private fun MethodAssembly.evaluatePropertyName(node: ASTNode) {
        if (node is PropertyNameNode) {
            if (node.isComputed) {
                compileExpression(node.expr)
                getValue
                operation("toPropertyKey", PropertyKey::class, JSValue::class)
                invokevirtual(PropertyKey::class, "getAsValue", JSValue::class)
            } else when (val expr = node.expr) {
                is IdentifierNode -> construct(JSString::class, String::class) {
                    ldc(expr.identifierName)
                }
                is StringLiteralNode, is NumericLiteralNode -> compileExpression(expr)
                else -> unreachable()
            }
        } else TODO()
    }

    private fun MethodAssembly.compileArrayLiteral(node: ArrayLiteralNode) {
        ldc(node.elements.size)
        operation("arrayCreate", JSObject::class)
        stackHeight++
        if (node.elements.isEmpty())
            return

        node.elements.forEachIndexed { index, element ->
            dup
            construct(JSNumber::class, Int::class) {
                ldc(index)
            }
            when (element.type) {
                ArrayElementNode.Type.Normal -> {
                    compileExpression(element.expression!!)
                    getValue
                    operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                    pop
                    stackHeight--
                }
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Elision -> { }
            }
        }
    }

    private fun MethodAssembly.compileMemberExpression(node: MemberExpressionNode) {
        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                compileExpression(node.lhs)
                getValue
                // TODO: Strict mode
                compileExpression(node.rhs)
                getValue
                operation("isStrict", Boolean::class)
                operation("evaluatePropertyAccessWithExpressionKey", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                stackHeight--
            }
            MemberExpressionNode.Type.NonComputed -> {
                compileExpression(node.lhs)
                getValue
                // TODO: Strict mode
                ldc((node.rhs as IdentifierNode).identifierName)
                operation("isStrict", Boolean::class)
                operation("evaluatePropertyAccessWithIdentifierKey", JSValue::class, JSValue::class, String::class, Boolean::class)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }
    }

    private fun MethodAssembly.compileOptionalExpression(node: ExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileAssignmentExpression(node: AssignmentExpressionNode) {
        val (lhs, rhs) = node.let { it.lhs to it.rhs }

        if (lhs.let { it is ObjectLiteralNode && it is ArrayLiteralNode })
            TODO()

        when (node.op) {
            AssignmentExpressionNode.Operator.Equals -> {
                compileExpression(lhs)
                getValue
                if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode) {
                    dup
                    checkcast<JSFunction>()
                    construct(PropertyKey::class, String::class) {
                        ldc(lhs.identifierName)
                    }
                    operation("setFunctionName", Boolean::class, JSFunction::class, PropertyKey::class)
                    pop
                }
                dup_x1
                operation("putValue", void, JSValue::class, JSValue::class)
            }
            AssignmentExpressionNode.Operator.And -> {
                compileExpression(lhs)
                dup
                getValue
                operation("toBoolean", Boolean::class, JSValue::class)
                ifStatement(JumpCondition.True) {
                    if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode)
                        TODO()
                    compileExpression(rhs)
                    dup_x1
                    operation("putValue", void, JSValue::class, JSValue::class)
                    stackHeight--
                }
            }
            AssignmentExpressionNode.Operator.Or -> {
                compileExpression(lhs)
                dup
                getValue
                operation("toBoolean", Boolean::class, JSValue::class)
                ifStatement(JumpCondition.False) {
                    if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode)
                        TODO()
                    compileExpression(rhs)
                    dup_x1
                    operation("putValue", void, JSValue::class, JSValue::class)
                    stackHeight--
                }
            }
            AssignmentExpressionNode.Operator.Nullish -> {
                compileExpression(lhs)
                dup
                getValue
                invokevirtual(JSValue::class, "isNullish", Boolean::class)
                ifStatement(JumpCondition.True) {
                    if (Operations.isAnonymousFunctionDefinition(rhs) && lhs is IdentifierReferenceNode)
                        TODO()
                    compileExpression(rhs)
                    dup_x1
                    operation("putValue", void, JSValue::class, JSValue::class)
                    stackHeight--
                }
            }
            else -> {
                compileExpression(lhs)
                getValue
                compileExpression(rhs)
                getValue
                ldc(node.op.symbol.dropLast(1))
                operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
                dup_x1
                operation("putValue", void, JSValue::class, JSValue::class)
                stackHeight--
            }
        }
    }

    private fun MethodAssembly.compileConditionalExpression(node: ConditionalExpressionNode) {
        compileExpression(node.predicate)
        getValue
        toBoolean
        stackHeight--
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                compileExpression(node.ifTrue)
            }

            elseBlock {
                compileExpression(node.ifFalse)
            }
        }
        getValue
    }

    private fun MethodAssembly.compileCoalesceExpression(node: CoalesceExpressionNode) {
        compileExpression(node.lhs)
        getValue
        dup
        invokevirtual(JSValue::class, "isNullish", Boolean::class)
        ifStatement(JumpCondition.False) {
            pop
            stackHeight--
            compileExpression(node.rhs)
            getValue
        }
    }

    private fun MethodAssembly.compileLogicalORExpression(node: LogicalORExpressionNode) {
        compileExpression(node.lhs)
        getValue
        dup
        toBoolean
        ifStatement(JumpCondition.False) {
            pop
            compileExpression(node.rhs)
            getValue
            stackHeight--
        }
    }

    private fun MethodAssembly.compileLogicalANDExpression(node: LogicalANDExpressionNode) {
        compileExpression(node.lhs)
        getValue
        dup
        toBoolean
        ifStatement(JumpCondition.True) {
            pop
            compileExpression(node.rhs)
            getValue
            stackHeight--
        }
    }

    private fun MethodAssembly.evaluateStringOrNumericBinaryExpression(lhs: ExpressionNode, rhs: ExpressionNode, op: String) {
        compileExpression(lhs)
        getValue
        compileExpression(rhs)
        getValue
        ldc(op)
        stackHeight--
        operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
    }

    private fun MethodAssembly.compileBitwiseORExpression(node: BitwiseORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(node.lhs, node.rhs, "|")
    }

    private fun MethodAssembly.compileBitwiseXORExpression(node: BitwiseXORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(node.lhs, node.rhs, "^")
    }

    private fun MethodAssembly.compileBitwiseANDExpression(node: BitwiseANDExpressionNode) {
        evaluateStringOrNumericBinaryExpression(node.lhs, node.rhs, "&")
    }

    private fun MethodAssembly.compileEqualityExpression(node: EqualityExpressionNode) {
        compileExpression(node.lhs)
        getValue
        compileExpression(node.rhs)
        getValue
        stackHeight--

        when (node.op) {
            EqualityExpressionNode.Operator.StrictEquality -> {
                swap
                operation("strictEqualityCompariso", JSValue::class, JSValue::class, JSValue::class)
            }
            EqualityExpressionNode.Operator.StrictInequality -> {
                swap
                operation("strictEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
                invertBoolean()
            }
            EqualityExpressionNode.Operator.NonstrictEquality -> {
                swap
                operation("abstractEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
            }
            EqualityExpressionNode.Operator.NonstrictInequality -> {
                swap
                operation("abstractEqualityComparison", JSValue::class, JSValue::class, JSValue::class)
                invertBoolean()
            }
        }
    }

    private fun MethodAssembly.compileRelationalExpression(node: RelationalExpressionNode) {
        compileExpression(node.lhs)
        getValue
        compileExpression(node.rhs)
        getValue
        stackHeight--

        when (node.op) {
            RelationalExpressionNode.Operator.LessThan -> {
                ldc(true)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                loadUndefined()
                ifStatement(JumpCondition.RefEqual) {
                    pop
                    loadFalse()
                }
            }
            RelationalExpressionNode.Operator.GreaterThan -> {
                swap
                ldc(false)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                dup
                loadUndefined()
                ifStatement(JumpCondition.RefEqual) {
                    pop
                    loadFalse()
                }
            }
            RelationalExpressionNode.Operator.LessThanEquals -> {
                swap
                ldc(false)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                invertBoolean()
            }
            RelationalExpressionNode.Operator.GreaterThanEquals -> {
                ldc(true)
                operation("abstractRelationalComparison", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
                invertBoolean()
            }
            RelationalExpressionNode.Operator.Instanceof ->
                operation("instanceofOperator", JSValue::class, JSValue::class, JSValue::class)
            RelationalExpressionNode.Operator.In -> {
                dup
                instanceof<JSObject>()
                ifStatement(JumpCondition.False) {
                    pop
                    loadKObject<Errors.InBadRHS>()
                    invokevirtual(Error::class, "throwTypeError", Nothing::class)
                    loadUndefined()
                    areturn
                }
                swap
                operation("toPropertyKey", PropertyKey::class, JSValue::class)
                operation("hasProperty", Boolean::class, JSValue::class, PropertyKey::class)
                new<JSBoolean>()
                dup_x1
                swap
                invokespecial(JSBoolean::class, "<init>", Boolean::class)
            }
        }
    }

    private fun MethodAssembly.compileShiftExpression(node: ShiftExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            when (node.op) {
                ShiftExpressionNode.Operator.ShiftLeft -> "<<"
                ShiftExpressionNode.Operator.ShiftRight -> ">>"
                ShiftExpressionNode.Operator.UnsignedShiftRight -> ">>>"
            },
        )
    }

    private fun MethodAssembly.compileAdditiveExpression(node: AdditiveExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            if (node.isSubtraction) "-" else "+",
        )
    }

    private fun MethodAssembly.compileMultiplicationExpression(node: MultiplicativeExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            when (node.op) {
                MultiplicativeExpressionNode.Operator.Multiply -> "*"
                MultiplicativeExpressionNode.Operator.Divide -> "/"
                MultiplicativeExpressionNode.Operator.Modulo -> "%"
            },
        )
    }

    private fun MethodAssembly.compileExponentiationExpression(node: ExponentiationExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            node.lhs,
            node.rhs,
            "**",
        )
    }

    private fun MethodAssembly.compileUnaryExpression(node: UnaryExpressionNode) {
        compileExpression(node.node)

        when (node.op) {
            UnaryExpressionNode.Operator.Delete -> operation("deleteOperator", JSValue::class, JSValue::class)
            UnaryExpressionNode.Operator.Void -> {
                getValue
                loadUndefined()
            }
            UnaryExpressionNode.Operator.Typeof -> {
                getValue
                operation("typeofOperator", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Plus -> {
                getValue
                operation("toNumber", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Minus -> {
                getValue
                operation("toNumeric", JSValue::class, JSValue::class)
                dup
                instanceof<JSBigInt>()
                ifStatement(JumpCondition.True) {
                    pop
                    construct(Errors.TODO::class, String::class) {
                        ldc("compileUnaryExpression, -BigInt")
                    }
                    invokevirtual(Error::class, "throwTypeError", Nothing::class)
                    loadUndefined()
                    areturn
                }
                operation("numericUnaryMinus", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.BitwiseNot -> {
                getValue
                operation("toNumeric", JSValue::class, JSValue::class)
                dup
                instanceof<JSBigInt>()
                ifStatement(JumpCondition.True) {
                    pop
                    construct(Errors.TODO::class, String::class) {
                        ldc("compileUnaryExpression, -BigInt")
                    }
                    invokevirtual(Error::class, "throwTypeError", Nothing::class)
                    loadUndefined()
                    areturn
                }
                operation("numericBitwiseNOT", JSValue::class, JSValue::class)
            }
            UnaryExpressionNode.Operator.Not -> {
                getValue
                toBoolean
                invertBoolean()
            }
        }
    }

    private fun MethodAssembly.compileUpdateExpression(node: UpdateExpressionNode) {
        compileExpression(node.target)
        dup
        // expr, expr
        getValue
        dup
        // expr, oldValue, oldValue
        instanceof<JSBigInt>()
        // expr, oldValue, boolean
        ifStatement(JumpCondition.True) {
            // expr, oldValue
            pop2
            construct(Errors.TODO::class, String::class) {
                ldc("compileUpdateExpression, BigInt")
            }
            invokevirtual(Error::class, "throwTypeError", Nothing::class)
            loadUndefined()
            areturn
        }
        // expr, oldValue
        dup_x1
        // oldValue, expr, oldValue
        construct(JSNumber::class, Double::class) {
            ldc(1.0)
        }
        // oldValue, expr, oldValue, 1.0
        if (node.isIncrement) {
            operation("numericAdd", JSValue::class, JSValue::class, JSValue::class)
        } else {
            operation("numericSubtract", JSValue::class, JSValue::class, JSValue::class)
        }
        // oldValue, expr, newValue
        dup_x1
        // oldValue, newValue, expr, newValue
        operation("putValue", void, JSValue::class, JSValue::class)
        // oldValue, newValue
        if (!node.isPostfix)
            swap
        pop
    }

    private fun MethodAssembly.compileForBinding(node: ExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileTemplateLiteral(node: ExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileClassExpression(node: ExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileSuperProperty(node: ExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileSuperCall(node: ExpressionNode) {
        TODO()
    }

    private inline fun <reified T> MethodAssembly.loadKObject() {
        getstatic(T::class, "INSTANCE", T::class)
    }

    private fun MethodAssembly.loadUndefined() = loadKObject<JSUndefined>()

    private fun MethodAssembly.loadNull() = loadKObject<JSNull>()

    private fun MethodAssembly.loadTrue() = loadKObject<JSTrue>()

    private fun MethodAssembly.loadFalse() = loadKObject<JSFalse>()

    private fun MethodAssembly.invertBoolean() {
        loadTrue()
        ifElseStatement(JumpCondition.RefEqual) {
            ifBlock {
                loadFalse()
            }

            elseBlock {
                loadTrue()
            }
        }
    }

    private fun MethodAssembly.operation(name: String, returnType: TypeLike, vararg parameterTypes: TypeLike) {
        invokestatic(Operations::class, name, returnType, *parameterTypes)
    }

    val MethodAssembly.getValue: Unit
        get() = operation("getValue", JSValue::class, JSValue::class)

    val MethodAssembly.toBoolean: Unit
        get() = operation("toBoolean", Boolean::class, JSValue::class)

    private fun MethodAssembly.createDeclarativeEnvRecord() {
        invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
    }

    private fun MethodAssembly.loadContext() {
        invokestatic(Agent::class, "getRunningContext", ExecutionContext::class)
    }

    private fun MethodAssembly.loadRealm() {
        loadContext()
        getfield(ExecutionContext::class, "realm", Realm::class)
    }

    private fun MethodAssembly.loadVariableEnv() {
        loadContext()
        getfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
    }

    private fun MethodAssembly.loadLexicalEnv() {
        loadContext()
        getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
    }

    private fun MethodAssembly.storeVariableEnv() {
        loadContext()
        swap
        putfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
    }

    private fun MethodAssembly.storeLexicalEnv() {
        loadContext()
        swap
        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
    }

    private fun MethodAssembly.tryCatchBuilder(block: TryCatchBuilder.() -> Unit) {
        val builder = TryCatchBuilder().apply(block)
        expect(builder.catchBlocks.isNotEmpty() || builder.finallyBlock != null)

        val finallyBlock = builder.finallyBlock

        val mainStart = makeLabel()
        val mainEnd = makeLabel()
        val lastFinallyBlock = if (finallyBlock != null) makeLabel() else null
        val tryCatchFinallyEnd = makeLabel()

        placeLabel(mainStart)
        verifyConsistentStackHeight {
            builder.tryBlock()
        }
        placeLabel(mainEnd)
        verifyConsistentStackHeight {
            finallyBlock?.invoke()
        }
        goto(tryCatchFinallyEnd)

        builder.catchBlocks.forEach {
            val catchStart = makeLabel()
            val catchEnd = makeLabel()

            placeLabel(catchStart)
            verifyConsistentStackHeight {
                it.second()
            }
            placeLabel(catchEnd)
            verifyConsistentStackHeight {
                finallyBlock?.invoke()
            }
            goto(tryCatchFinallyEnd)

            tryCatchBlocks.add(TryCatchBlockNode(
                mainStart,
                mainEnd,
                catchStart,
                coerceType(it.first).internalName,
            ))

            if (lastFinallyBlock != null) {
                tryCatchBlocks.add(TryCatchBlockNode(
                    catchStart,
                    catchEnd,
                    lastFinallyBlock,
                    null
                ))
            }
        }

        if (lastFinallyBlock != null) {
            placeLabel(lastFinallyBlock)
            tryCatchBlocks.add(TryCatchBlockNode(
                mainStart,
                mainEnd,
                lastFinallyBlock,
                null,
            ))

            val exception = astore()
            verifyConsistentStackHeight {
                finallyBlock!!()
            }
            load(exception)
            athrow
        }

        placeLabel(tryCatchFinallyEnd)
    }

    private fun verifyConsistentStackHeight(block: () -> Unit) {
        val initialStackHeight = stackHeight
        block()
        if (initialStackHeight != stackHeight)
            throw IllegalStateException("verifyConsistentStackHeight failed. Initial: $initialStackHeight, final: $stackHeight")
    }

    private class TryCatchBuilder {
        lateinit var tryBlock: (() -> Unit)
        val catchBlocks = mutableListOf<Pair<TypeLike, () -> Unit>>()
        var finallyBlock: (() -> Unit)? = null

        fun tryBlock(block: () -> Unit) {
            tryBlock = block
        }

        inline fun <reified T> catchBlock(noinline block: () -> Unit) {
            catchBlocks.add(T::class to block)
        }

        fun finallyBlock(block: () -> Unit) {
            finallyBlock = block
        }
    }

    // helper JVM bytecodes
    // a, b, c -> a, c
    private val MethodAssembly.pop_x1: Unit
        get() {
            swap
            pop
        }

    // a, b, c -> c, b, a
    private val MethodAssembly.swap_x1: Unit
        get() {
            dup_x2
            pop
            swap
        }

    private val MethodAssembly.pop3: Unit
        get() {
            pop2
            pop
        }

    private val MethodAssembly.pop4: Unit
        get() {
            pop2
            pop2
        }
}
