package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSBuiltinFunction private constructor(
    realm: Realm,
    name: String,
    length: Int,
    val builtin: Builtin,
    prototype: JSValue = realm.functionProto,
) : JSNativeFunction(realm, name, length, prototype, builtin.debugName) {
    override fun isConstructor() = false

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            Errors.NotACtor(name).throwTypeError()
        return builtin.handle.invokeExact(arguments) as JSValue
    }

    companion object {
        fun create(
            name: String,
            length: Int,
            builtin: Builtin,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            prototype: JSValue = realm.functionProto,
        ) = JSBuiltinFunction(realm, name, length, builtin, prototype).initialize()
    }
}
