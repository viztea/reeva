package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSEmpty

class JSMapObject private constructor(realm: Realm) : JSObject(realm, realm.mapProto) {
    val mapData by slot(Slot.MapData, MapData())

    data class MapData(
        val map: MutableMap<JSValue, JSValue> = mutableMapOf(),
        val keyInsertionOrder: MutableList<JSValue> = mutableListOf(),
    ) {
        var iterationCount = 0
            set(value) {
                if (value == 0)
                    keyInsertionOrder.removeIf { it == JSEmpty }
                field = value
            }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSMapObject(realm).initialize()
    }
}
