package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.arrays.JSArray
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.toValue

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    @JSMethod("toString", 0)
    fun toString_(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        if (thisValue == JSUndefined)
            return "[object Undefined]".toValue()
        if (thisValue == JSNull)
            return "[object Null]".toValue()

        val builtinTag = when (Operations.toObject(thisValue)) {
            is JSArray -> "Array"
            is JSFunction -> "Function"
            else -> TODO()
        }

        // TODO: @@toStringTag
        return "[object $builtinTag]".toValue()
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSObjectProto(realm)
    }
}
