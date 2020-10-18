package me.mattco.jsthing.runtime.values.nonprimitives.objects

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSNativeFunction

class JSObjectCtor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor") {
    override fun call(context: ExecutionContext, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(context: ExecutionContext, newTarget: JSObject, arguments: List<JSValue>): JSValue {
        TODO("Not yet implemented")
    }
}
