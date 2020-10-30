package me.mattco.reeva.runtime

import me.mattco.reeva.ast.BindingIdentifierNode
import me.mattco.reeva.ast.FunctionDeclarationNode
import me.mattco.reeva.ast.statements.ForBindingNode
import me.mattco.reeva.ast.statements.StatementListNode
import me.mattco.reeva.ast.statements.VariableDeclarationNode
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Completion
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.environment.*
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.errors.JSSyntaxErrorObject
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

open class JSGlobalObject protected constructor(
    realm: Realm,
    proto: JSObject = realm.objectProto
) : JSObject(realm, proto) {
    override fun init() {
        super.init()

        set("Array", realm.arrayCtor)
        set("Boolean", realm.booleanCtor)
        set("Error", realm.errorCtor)
        set("EvalError", realm.evalErrorCtor)
        set("Function", realm.functionCtor)
        set("Number", realm.numberCtor)
        set("Object", realm.objectCtor)
        set("Proxy", realm.proxyCtor)
        set("RangeError", realm.rangeErrorCtor)
        set("ReferenceError", realm.referenceErrorCtor)
        set("Reflect", realm.reflectObj)
        set("String", realm.stringCtor)
        set("Symbol", realm.symbolCtor)
        set("SyntaxError", realm.syntaxErrorCtor)
        set("TypeError", realm.typeErrorCtor)
        set("URIError", realm.uriErrorCtor)

        set("Math", realm.mathObj)
        set("JSON", realm.jsonObj)
        set("console", realm.consoleObj)

        defineOwnProperty("Infinity", JSNumber.POSITIVE_INFINITY, 0)
        defineOwnProperty("NaN", JSNumber.NaN, 0)
        defineOwnProperty("globalThis", this, Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        defineOwnProperty("undefined", JSUndefined, 0)
        defineNativeFunction("id".key(), 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::id)
        defineNativeFunction("eval".key(), 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE, ::eval)
    }

    fun id(thisValue: JSValue, arguments: JSArguments): JSValue {
        val o = arguments.argument(0)
        return "${o::class.java.simpleName}@${Integer.toHexString(o.hashCode())}".toValue()
    }

    fun eval(thisValue: JSValue, arguments: JSArguments): JSValue {
        return performEval(arguments.argument(0), strictCaller = false, direct = false)
    }

    @JSThrows @ECMAImpl("18.2.1.1")
    private fun performEval(argument: JSValue, strictCaller: Boolean, direct: Boolean): JSValue {
        if (!direct)
            ecmaAssert(!strictCaller)

        if (argument !is JSString)
            return argument

        val evalRealm = Agent.runningContext.realm
        var inFunction = false
        var inMethod = false
        var inDerivedConstructor = false

        if (direct) {
            val thisEnv = Operations.getThisEnvironment()
            if (thisEnv is FunctionEnvRecord) {
                val function = thisEnv.function
                inFunction = true
                inMethod = thisEnv.hasSuperBinding()
                if (function.constructorKind == JSFunction.ConstructorKind.Derived)
                    inDerivedConstructor = true
            }
        }

        val parser = Parser(argument.string)
        val scriptNode = parser.parseScript()

        if (parser.syntaxErrors.isNotEmpty()) {
            throwError<JSSyntaxErrorObject>(parser.syntaxErrors.first().message)
            return INVALID_VALUE
        }

        val body = scriptNode.statementList
        if (!inFunction && body.contains("NewTargetNode")) {
            throwError<JSSyntaxErrorObject>("new.target accessed outside of a function")
            return INVALID_OBJECT
        }

        if (!inMethod && body.contains("SuperPropertyNode")) {
            throwError<JSSyntaxErrorObject>("super property accessed outside of a method")
            return INVALID_OBJECT
        }

        if (!inDerivedConstructor && body.contains("SuperCallNode")) {
            throwError<JSSyntaxErrorObject>("super call outside of a constructor")
            return INVALID_OBJECT
        }

        val strictEval = strictCaller || scriptNode.isStrict()
        val context = Agent.runningContext
        var (varEnv, lexEnv) = if (direct) {
            context.variableEnv!! to DeclarativeEnvRecord.create(context.lexicalEnv)
        } else {
            evalRealm.globalEnv to DeclarativeEnvRecord.create(evalRealm.globalEnv)
        }

        if (strictEval)
            varEnv = lexEnv

        val evalContext = ExecutionContext(evalRealm, null)
        evalContext.variableEnv = varEnv
        evalContext.lexicalEnv = lexEnv
        Agent.pushContext(evalContext)

        val interpreter = Interpreter(realm, scriptNode)

        var result = evalDeclarationInstantiation(scriptNode.statementList, varEnv, lexEnv, strictEval, interpreter)
        if (result.isNormal)
            result = interpreter.interpret()
        if (result.isNormal && result.value == JSEmpty)
            result = Completion(Completion.Type.Normal, JSUndefined)

        Agent.popContext()
        return result.value
    }

    private fun evalDeclarationInstantiation(
        body: StatementListNode,
        varEnv: EnvRecord,
        lexEnv: EnvRecord,
        strictEval: Boolean,
        interpreter: Interpreter,
    ): Completion {
        val varNames = body.varDeclaredNames()
        val varDeclarations = body.varScopedDeclarations()
        if (!strictEval) {
            if (varEnv is GlobalEnvRecord) {
                varNames.forEach { name ->
                    if (varEnv.hasLexicalDeclaration(name)) {
                        throwError<JSSyntaxErrorObject>("TODO: message")
                        return Completion(Completion.Type.Throw, Agent.runningContext.error!!)
                    }
                }
            }
            var thisEnv = lexEnv
            while (thisEnv != varEnv) {
                if (thisEnv !is ObjectEnvRecord) {
                    varNames.forEach { name ->
                        if (thisEnv.hasBinding(name)) {
                            throwError<JSSyntaxErrorObject>("TODO: message")
                            return Completion(Completion.Type.Throw, Agent.runningContext.error!!)
                        }
                    }
                }
                thisEnv = thisEnv.outerEnv!!
            }
        }
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        varDeclarations.asReversed().forEach { decl ->
            if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode)
                return@forEach
            val functionName = decl.boundNames()[0]
            if (functionName !in declaredFunctionNames) {
                if (varEnv is GlobalEnvRecord) {
                    if (!varEnv.canDeclareGlobalFunction(functionName)) {
                        throwError<JSTypeErrorObject>("TODO: message")
                        return Completion(Completion.Type.Throw, Agent.runningContext.error!!)
                    }
                    declaredFunctionNames.add(functionName)
                    functionsToInitialize.add(0, decl as FunctionDeclarationNode)
                }
            }
        }

        val declaredVarNames = mutableListOf<String>()
        varDeclarations.forEach { decl ->
            if (decl !is VariableDeclarationNode && decl !is ForBindingNode && decl !is BindingIdentifierNode)
                return@forEach
            decl.boundNames().forEach { name ->
                if (name !in declaredFunctionNames) {
                    if (varEnv is GlobalEnvRecord) {
                        if (!varEnv.canDeclareGlobalVar(name)) {
                            throwError<JSTypeErrorObject>("TODO: message")
                            return Completion(Completion.Type.Throw, Agent.runningContext.error!!)
                        }
                    }
                    if (name !in declaredVarNames)
                        declaredVarNames.add(name)
                }
            }
        }
        body.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                if (decl.isConstantDeclaration()) {
                    lexEnv.createImmutableBinding(name, true)
                } else {
                    lexEnv.createMutableBinding(name, false)
                }
            }
        }
        functionsToInitialize.forEach { func ->
            val functionName = func.boundNames()[0]
            val function = interpreter.instantiateFunctionObject(func, lexEnv)
            if (varEnv is GlobalEnvRecord) {
                varEnv.createGlobalFunctionBinding(functionName, function, true)
            } else {
                if (!varEnv.hasBinding(functionName)) {
                    varEnv.createMutableBinding(functionName, true)
                    // TODO: Validate above step
                    varEnv.initializeBinding(functionName, function)
                } else {
                    varEnv.setMutableBinding(functionName, function, false)
                }
            }
        }
        declaredVarNames.forEach { name ->
            if (varEnv is GlobalEnvRecord) {
                varEnv.createGlobalVarBinding(name, true)
            } else if (!varEnv.hasBinding(name)) {
                varEnv.createMutableBinding(name, true)
                // TODO: Validate above step
                varEnv.initializeBinding(name, JSUndefined)
            }
        }
        return Completion(Completion.Type.Normal, JSEmpty)
    }

    companion object {
        fun create(realm: Realm) = JSGlobalObject(realm).also { it.init() }
    }
}
