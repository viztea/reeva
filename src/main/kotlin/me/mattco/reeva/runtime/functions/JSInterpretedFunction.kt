package me.mattco.reeva.runtime.functions

import me.mattco.reeva.interpreter.Completion
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect

class JSInterpretedFunction(
    realm: Realm,
    thisMode: ThisMode,
    envRecord: EnvRecord?,
    isStrict: Boolean,
    isClassConstructor: Boolean,
    homeObject: JSValue,
    prototype: JSObject = realm.functionProto,
    internal val sourceText: String,
    private val evalBody: (JSInterpretedFunction, JSArguments) -> Completion,
) : JSFunction(
    realm,
    thisMode,
    envRecord,
    homeObject,
    isClassConstructor,
    isStrict,
    prototype
) {
    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (isClassConstructor) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val calleeContext = Operations.prepareForOrdinaryCall(this, JSUndefined)
        if (Agent.hasError())
            return INVALID_VALUE
        ecmaAssert(Agent.runningContext == calleeContext)
        Operations.ordinaryCallBindThis(this, calleeContext, thisValue)
        val result = evalBody(this, arguments)
        Agent.popContext()
        if (result.isReturn)
            return result.value
        if (result.isAbrupt) {
            Agent.throwError(result.value)
            return INVALID_VALUE
        }
        return JSUndefined
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        ecmaAssert(newTarget is JSObject)

        val thisArgument = if (constructorKind == ConstructorKind.Base) {
            Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
        } else null

        val calleeContext = Operations.prepareForOrdinaryCall(this, newTarget)
        ecmaAssert(Agent.runningContext == calleeContext)
        if (constructorKind == ConstructorKind.Base)
            Operations.ordinaryCallBindThis(this, calleeContext, thisArgument!!)
        val constructorEnv = calleeContext.lexicalEnv
        expect(constructorEnv is FunctionEnvRecord)
        val result = evalBody(this, arguments)
        Agent.popContext()
        if (result.isReturn) {
            if (result.value is JSObject)
                return result.value
            if (constructorKind == ConstructorKind.Base)
                return thisArgument!!
            if (result.value != JSUndefined) {
                throwError<JSTypeErrorObject>("TODO: message")
                return INVALID_VALUE
            }
        } else if (result.isAbrupt) {
            return result.value
        }
        return constructorEnv.getThisBinding()

    }
}
