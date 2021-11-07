package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.jvmcompat.JSClassObject
import com.reevajs.reeva.jvmcompat.JSClassProto
import com.reevajs.reeva.jvmcompat.JSPackageProto
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.arrays.JSArrayCtor
import com.reevajs.reeva.runtime.arrays.JSArrayProto
import com.reevajs.reeva.runtime.collections.*
import com.reevajs.reeva.runtime.errors.JSErrorProto
import com.reevajs.reeva.runtime.functions.JSFunctionProto
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObjectProto
import com.reevajs.reeva.runtime.global.JSConsoleProto
import com.reevajs.reeva.runtime.iterators.*
import com.reevajs.reeva.runtime.memory.*
import com.reevajs.reeva.runtime.objects.JSObjectCtor
import com.reevajs.reeva.runtime.objects.JSObjectProto
import com.reevajs.reeva.runtime.other.JSDateCtor
import com.reevajs.reeva.runtime.other.JSDateProto
import com.reevajs.reeva.runtime.other.JSProxyCtor
import com.reevajs.reeva.runtime.promises.JSPromiseCtor
import com.reevajs.reeva.runtime.promises.JSPromiseProto
import com.reevajs.reeva.runtime.regexp.JSRegExpCtor
import com.reevajs.reeva.runtime.regexp.JSRegExpProto
import com.reevajs.reeva.runtime.regexp.JSRegExpStringIteratorProto
import com.reevajs.reeva.runtime.singletons.JSMathObject
import com.reevajs.reeva.runtime.singletons.JSONObject
import com.reevajs.reeva.runtime.singletons.JSReflectObject
import com.reevajs.reeva.runtime.wrappers.*
import com.reevajs.reeva.runtime.wrappers.strings.JSStringCtor
import com.reevajs.reeva.runtime.wrappers.strings.JSStringIteratorProto
import com.reevajs.reeva.runtime.wrappers.strings.JSStringProto
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

enum class ReevaBuiltin(clazz: Class<*>, name: String, override val debugName: String) : Builtin {
    ArrayCtorGetSymbolSpecies(JSArrayCtor::class.java, "getSymbolSpecies", "Array[@@species]"),
    ArrayCtorIsArray(JSArrayCtor::class.java, "isArray", "Array.isArray"),
    ArrayCtorFrom(JSArrayCtor::class.java, "from", "Array.from"),
    ArrayCtorOf(JSArrayCtor::class.java, "of", "Array.of"),
    ArrayProtoAt(JSArrayProto::class.java, "at", "Array.prototype.at"),
    ArrayProtoConcat(JSArrayProto::class.java, "concat", "Array.prototype.concat"),
    ArrayProtoCopyWithin(JSArrayProto::class.java, "copyWithin", "Array.prototype.copyWithin"),
    ArrayProtoEntries(JSArrayProto::class.java, "entries", "Array.prototype.entries"),
    ArrayProtoEvery(JSArrayProto::class.java, "every", "Array.prototype.every"),
    ArrayProtoFill(JSArrayProto::class.java, "fill", "Array.prototype.fill"),
    ArrayProtoFilter(JSArrayProto::class.java, "filter", "Array.prototype.filter"),
    ArrayProtoFind(JSArrayProto::class.java, "find", "Array.prototype.find"),
    ArrayProtoFindIndex(JSArrayProto::class.java, "findIndex", "Array.prototype.findIndex"),
    ArrayProtoFlat(JSArrayProto::class.java, "flat", "Array.prototype.flat"),
    ArrayProtoFlatMap(JSArrayProto::class.java, "flatMap", "Array.prototype.flatMap"),
    ArrayProtoForEach(JSArrayProto::class.java, "forEach", "Array.prototype.forEach"),
    ArrayProtoIncludes(JSArrayProto::class.java, "includes", "Array.prototype.includes"),
    ArrayProtoIndexOf(JSArrayProto::class.java, "indexOf", "Array.prototype.indexOf"),
    ArrayProtoJoin(JSArrayProto::class.java, "join", "Array.prototype.join"),
    ArrayProtoKeys(JSArrayProto::class.java, "keys", "Array.prototype.keys"),
    ArrayProtoLastIndexOf(JSArrayProto::class.java, "lastIndexOf", "Array.prototype.lastIndexOf"),
    ArrayProtoMap(JSArrayProto::class.java, "map", "Array.prototype.map"),
    ArrayProtoPop(JSArrayProto::class.java, "pop", "Array.prototype.pop"),
    ArrayProtoPush(JSArrayProto::class.java, "push", "Array.prototype.push"),
    ArrayProtoReduce(JSArrayProto::class.java, "reduce", "Array.prototype.reduce"),
    ArrayProtoReduceRight(JSArrayProto::class.java, "reduceRight", "Array.prototype.reduceRight"),
    ArrayProtoReverse(JSArrayProto::class.java, "reverse", "Array.prototype.reverse"),
    ArrayProtoShift(JSArrayProto::class.java, "shift", "Array.prototype.shift"),
    ArrayProtoSlice(JSArrayProto::class.java, "slice", "Array.prototype.slice"),
    ArrayProtoSome(JSArrayProto::class.java, "some", "Array.prototype.some"),
    ArrayProtoSplice(JSArrayProto::class.java, "splice", "Array.prototype.splice"),
    ArrayProtoToString(JSArrayProto::class.java, "toString", "Array.prototype.toString"),
    ArrayProtoUnshift(JSArrayProto::class.java, "unshift", "Array.prototype.unshift"),
    ArrayProtoValues(JSArrayProto::class.java, "values", "Array.prototype.values"),

    ArrayBufferCtorIsView(JSArrayBufferCtor::class.java, "isView", "ArrayBuffer.isView"),
    ArrayBufferCtorGetSymbolSpecies(JSArrayBufferCtor::class.java, "getSymbolSpecies", "ArrayBuffer[@@species]"),
    ArrayBufferProtoGetByteLength(JSArrayBufferProto::class.java, "getByteLength", "ArrayBuffer.prototype.getByteLength"),
    ArrayBufferProtoSlice(JSArrayBufferProto::class.java, "slice", "ArrayBuffer.prototype.slice"),

    ArrayIteratorProtoNext(JSArrayIteratorProto::class.java, "next", "ArrayIterator.prototype.next"),

    BigIntCtorAsIntN(JSBigIntCtor::class.java, "asIntN", "BigInt.asIntN"),
    BigIntCtorAsUintN(JSBigIntCtor::class.java, "asUintN", "BigInt.asUintN"),
    BigIntProtoToString(JSBigIntProto::class.java, "toString", "BigInt.prototype.toString"),
    BigIntProtoValueOf(JSBigIntProto::class.java, "valueOf", "BigInt.prototype.valueOf"),

    BooleanProtoToString(JSBooleanProto::class.java, "toString", "Boolean.prototype.toString"),
    BooleanProtoValueOf(JSBooleanProto::class.java, "valueOf", "Boolean.prototype.valueOf"),

    DataViewProtoGetBuffer(JSDataViewProto::class.java, "getBuffer", "DataView.prototype.getBuffer"),
    DataViewProtoGetByteLength(JSDataViewProto::class.java, "getByteLength", "DataView.prototype.getByteLength"),
    DataViewProtoGetByteOffset(JSDataViewProto::class.java, "getByteOffset", "DataView.prototype.getByteOffset"),
    DataViewProtoGetBigInt64(JSDataViewProto::class.java, "getBigInt64", "DataView.prototype.getBigInt64"),
    DataViewProtoGetBigUint64(JSDataViewProto::class.java, "getBigUint64", "DataView.prototype.getBigUint64"),
    DataViewProtoGetFloat32(JSDataViewProto::class.java, "getFloat32", "DataView.prototype.getFloat32"),
    DataViewProtoGetFloat64(JSDataViewProto::class.java, "getFloat64", "DataView.prototype.getFloat64"),
    DataViewProtoGetInt8(JSDataViewProto::class.java, "getInt8", "DataView.prototype.getInt8"),
    DataViewProtoGetInt16(JSDataViewProto::class.java, "getInt16", "DataView.prototype.getInt16"),
    DataViewProtoGetInt32(JSDataViewProto::class.java, "getInt32", "DataView.prototype.getInt32"),
    DataViewProtoGetUint8(JSDataViewProto::class.java, "getUint8", "DataView.prototype.getUint8"),
    DataViewProtoGetUint16(JSDataViewProto::class.java, "getUint16", "DataView.prototype.getUint16"),
    DataViewProtoGetUint32(JSDataViewProto::class.java, "getUint32", "DataView.prototype.getUint32"),
    DataViewProtoSetBigInt64(JSDataViewProto::class.java, "setBigInt64", "DataView.prototype.setBigInt64"),
    DataViewProtoSetBigUint64(JSDataViewProto::class.java, "setBigUint64", "DataView.prototype.setBigUint64"),
    DataViewProtoSetFloat32(JSDataViewProto::class.java, "setFloat32", "DataView.prototype.setFloat32"),
    DataViewProtoSetFloat64(JSDataViewProto::class.java, "setFloat64", "DataView.prototype.setFloat64"),
    DataViewProtoSetInt8(JSDataViewProto::class.java, "setInt8", "DataView.prototype.setInt8"),
    DataViewProtoSetInt16(JSDataViewProto::class.java, "setInt16", "DataView.prototype.setInt16"),
    DataViewProtoSetInt32(JSDataViewProto::class.java, "setInt32", "DataView.prototype.setInt32"),
    DataViewProtoSetUint8(JSDataViewProto::class.java, "setUint8", "DataView.prototype.setUint8"),
    DataViewProtoSetUint16(JSDataViewProto::class.java, "setUint16", "DataView.prototype.setUint16"),
    DataViewProtoSetUint32(JSDataViewProto::class.java, "setUint32", "DataView.prototype.setUint32"),

    DateCtorNow(JSDateCtor::class.java, "now", "Date.now"),
    DateCtorParse(JSDateCtor::class.java, "parse", "Date.parse"),
    DateCtorUTC(JSDateCtor::class.java, "utc", "Date.UTC"),
    DateProtoGetDate(JSDateProto::class.java, "getDate", "Date.prototype.getDate"),
    DateProtoGetDay(JSDateProto::class.java, "getDay", "Date.prototype.getDay"),
    DateProtoGetFullYear(JSDateProto::class.java, "getFullYear", "Date.prototype.getFullYear"),
    DateProtoGetHours(JSDateProto::class.java, "getHours", "Date.prototype.getHours"),
    DateProtoGetMilliseconds(JSDateProto::class.java, "getMilliseconds", "Date.prototype.getMilliseconds"),
    DateProtoGetMinutes(JSDateProto::class.java, "getMinutes", "Date.prototype.getMinutes"),
    DateProtoGetMonth(JSDateProto::class.java, "getMonth", "Date.prototype.getMonth"),
    DateProtoGetSeconds(JSDateProto::class.java, "getSeconds", "Date.prototype.getSeconds"),
    DateProtoGetTime(JSDateProto::class.java, "getTime", "Date.prototype.getTime"),
    DateProtoGetTimezoneOffset(JSDateProto::class.java, "getTimezoneOffset", "Date.prototype.getTimezoneOffset"),
    DateProtoGetUTCDate(JSDateProto::class.java, "getUTCDate", "Date.prototype.getUtcDate"),
    DateProtoGetUTCFullYear(JSDateProto::class.java, "getUTCFullYear", "Date.prototype.getUtcFullYear"),
    DateProtoGetUTCHours(JSDateProto::class.java, "getUTCHours", "Date.prototype.getUtcHours"),
    DateProtoGetUTCMilliseconds(JSDateProto::class.java, "getUTCMilliseconds", "Date.prototype.getUtcMilliseconds"),
    DateProtoGetUTCMinutes(JSDateProto::class.java, "getUTCMinutes", "Date.prototype.getUtcMinutes"),
    DateProtoGetUTCMonth(JSDateProto::class.java, "getUTCMonth", "Date.prototype.getUtcMonth"),
    DateProtoGetUTCSeconds(JSDateProto::class.java, "getUTCSeconds", "Date.prototype.getUtcSeconds"),
    DateProtoSetDate(JSDateProto::class.java, "setDate", "Date.prototype.setDate"),
    DateProtoSetFullYear(JSDateProto::class.java, "setFullYear", "Date.prototype.setFullYear"),
    DateProtoSetHours(JSDateProto::class.java, "setHours", "Date.prototype.setHours"),
    DateProtoSetMilliseconds(JSDateProto::class.java, "setMilliseconds", "Date.prototype.setMilliseconds"),
    DateProtoSetMonth(JSDateProto::class.java, "setMonth", "Date.prototype.setMonth"),
    DateProtoSetSeconds(JSDateProto::class.java, "setSeconds", "Date.prototype.setSeconds"),
    DateProtoSetTime(JSDateProto::class.java, "setTime", "Date.prototype.setTime"),
    DateProtoSetUTCDate(JSDateProto::class.java, "setUTCDate", "Date.prototype.setUtcDate"),
    DateProtoSetUTCFullYear(JSDateProto::class.java, "setUTCFullYear", "Date.prototype.setUtcFullYear"),
    DateProtoSetUTCHours(JSDateProto::class.java, "setUTCHours", "Date.prototype.setUtcHours"),
    DateProtoSetUTCMilliseconds(JSDateProto::class.java, "setUTCMilliseconds", "Date.prototype.setUtcMilliseconds"),
    DateProtoSetUTCMinutes(JSDateProto::class.java, "setUTCMinutes", "Date.prototype.setUtcMinutes"),
    DateProtoSetUTCMonth(JSDateProto::class.java, "setUTCMonth", "Date.prototype.setUtcMonth"),
    DateProtoSetUTCSeconds(JSDateProto::class.java, "setUTCSeconds", "Date.prototype.setUtcSeconds"),
    DateProtoToDateString(JSDateProto::class.java, "toDateString", "Date.prototype.toDateString"),
    DateProtoToISOString(JSDateProto::class.java, "toISOString", "Date.prototype.toIsoString"),
    DateProtoToJSON(JSDateProto::class.java, "toJSON", "Date.prototype.toJson"),
    DateProtoToString(JSDateProto::class.java, "toString", "Date.prototype.toString"),
    DateProtoToTimeString(JSDateProto::class.java, "toTimeString", "Date.prototype.toTimeString"),
    DateProtoToUTCString(JSDateProto::class.java, "toUTCString", "Date.prototype.toUtcString"),
    DateProtoValueOf(JSDateProto::class.java, "valueOf", "Date.prototype.valueOf"),
    DateProtoSymbolToPrimitive(JSDateProto::class.java, "symbolToPrimitive", "Date.prototype[@@toPrimitive]"),

    ErrorProtoToString(JSErrorProto::class.java, "toString", "Error.prototype.toString"),

    FunctionProtoApply(JSFunctionProto::class.java, "apply", "Function.prototype.apply"),
    FunctionProtoBind(JSFunctionProto::class.java, "bind", "Function.prototype.bind"),
    FunctionProtoCall(JSFunctionProto::class.java, "call", "Function.prototype.call"),

    GeneratorObjectProtoNext(JSGeneratorObjectProto::class.java, "next", "GeneratorObject.prototype.next"),
    GeneratorObjectProtoReturn(JSGeneratorObjectProto::class.java, "return_", "GeneratorObject.prototype.return"),
    GeneratorObjectProtoThrow(JSGeneratorObjectProto::class.java, "throw_", "GeneratorObject.prototype.throw"),

    GlobalEval(JSGlobalObject::class.java, "eval", "eval"),
    GlobalParseInt(JSGlobalObject::class.java, "parseInt", "parseInt"),

    IteratorProtoSymbolIterator(JSIteratorProto::class.java, "symbolIterator", "Iterator.prototype[@@iterator]"),

    JSONParse(JSONObject::class.java, "parse", "JSON.parse"),
    JSONStringify(JSONObject::class.java, "stringify", "JSON.stringify"),
    JSONGetSymbolToStringTag(JSONObject::class.java, "getSymbolToStringTag", "JSON[@@toStringTag]"),

    ListIteratorProtoNext(JSListIteratorProto::class.java, "next", "ListIterator.prototype.next"),

    MapCtorGetSymbolSpecies(JSMapCtor::class.java, "getSymbolSpecies", "Map[@@species]"),
    MapProtoClear(JSMapProto::class.java, "clear", "Map.prototype.clear"),
    MapProtoDelete(JSMapProto::class.java, "delete", "Map.prototype.delete"),
    MapProtoEntries(JSMapProto::class.java, "entries", "Map.prototype.entries"),
    MapProtoForEach(JSMapProto::class.java, "forEach", "Map.prototype.forEach"),
    MapProtoGet(JSMapProto::class.java, "get", "Map.prototype.get"),
    MapProtoHas(JSMapProto::class.java, "has", "Map.prototype.has"),
    MapProtoKeys(JSMapProto::class.java, "keys", "Map.prototype.keys"),
    MapProtoSet(JSMapProto::class.java, "set", "Map.prototype.set"),
    MapProtoValues(JSMapProto::class.java, "values", "Map.prototype.values"),
    MapProtoGetSize(JSMapProto::class.java, "getSize", "Map.prototype.getSize"),

    MapIteratorProtoNext(JSMapIteratorProto::class.java, "next", "MapIterator.prototype.next"),

    MathAbs(JSMathObject::class.java, "abs", "Math.abs"),
    MathAcos(JSMathObject::class.java, "acos", "Math.acos"),
    MathAcosh(JSMathObject::class.java, "acosh", "Math.acosh"),
    MathAsin(JSMathObject::class.java, "asin", "Math.asin"),
    MathAsinh(JSMathObject::class.java, "asinh", "Math.asinh"),
    MathAtan(JSMathObject::class.java, "atan", "Math.atan"),
    MathAtanh(JSMathObject::class.java, "atanh", "Math.atanh"),
    MathAtan2(JSMathObject::class.java, "atan2", "Math.atan2"),
    MathCbrt(JSMathObject::class.java, "cbrt", "Math.cbrt"),
    MathCeil(JSMathObject::class.java, "ceil", "Math.ceil"),
    MathClz32(JSMathObject::class.java, "clz32", "Math.clz32"),
    MathCos(JSMathObject::class.java, "cos", "Math.cos"),
    MathCosh(JSMathObject::class.java, "cosh", "Math.cosh"),
    MathExp(JSMathObject::class.java, "exp", "Math.exp"),
    MathExpm1(JSMathObject::class.java, "expm1", "Math.expm1"),
    MathFloor(JSMathObject::class.java, "floor", "Math.floor"),
    MathFround(JSMathObject::class.java, "fround", "Math.fround"),
    MathHypot(JSMathObject::class.java, "hypot", "Math.hypot"),
    MathImul(JSMathObject::class.java, "imul", "Math.imul"),
    MathLog(JSMathObject::class.java, "log", "Math.log"),
    MathLog1p(JSMathObject::class.java, "log1p", "Math.log1p"),
    MathLog10(JSMathObject::class.java, "log10", "Math.log10"),
    MathLog2(JSMathObject::class.java, "log2", "Math.log2"),
    MathMax(JSMathObject::class.java, "max", "Math.max"),
    MathMin(JSMathObject::class.java, "min", "Math.min"),
    MathPow(JSMathObject::class.java, "pow", "Math.pow"),
    MathRandom(JSMathObject::class.java, "random", "Math.random"),
    MathRound(JSMathObject::class.java, "round", "Math.round"),
    MathSign(JSMathObject::class.java, "sign", "Math.sign"),
    MathSin(JSMathObject::class.java, "sin", "Math.sin"),
    MathSinh(JSMathObject::class.java, "sinh", "Math.sinh"),
    MathSqrt(JSMathObject::class.java, "sqrt", "Math.sqrt"),
    MathTan(JSMathObject::class.java, "tan", "Math.tan"),
    MathTanh(JSMathObject::class.java, "tanh", "Math.tanh"),
    MathTrunc(JSMathObject::class.java, "trunc", "Math.trunc"),

    NumberCtorIsFinite(JSNumberCtor::class.java, "isFinite", "Number.isFinite"),
    NumberCtorIsInteger(JSNumberCtor::class.java, "isInteger", "Number.isInteger"),
    NumberCtorIsNaN(JSNumberCtor::class.java, "isNaN", "Number.isNaN"),
    NumberCtorIsSafeInteger(JSNumberCtor::class.java, "isSafeInteger", "Number.isSafeInteger"),
    NumberCtorParseFloat(JSNumberCtor::class.java, "parseFloat", "Number.parseFloat"),
    NumberCtorParseInt(JSNumberCtor::class.java, "parseInt", "Number.parseInt"),
    NumberProtoToExponential(JSNumberProto::class.java, "toExponential", "Number.prototype.toExponential"),
    NumberProtoToFixed(JSNumberProto::class.java, "toFixed", "Number.prototype.toFixed"),
    NumberProtoToLocaleString(JSNumberProto::class.java, "toLocaleString", "Number.prototype.toLocaleString"),
    NumberProtoToPrecision(JSNumberProto::class.java, "toPrecision", "Number.prototype.toPrecision"),
    NumberProtoToString(JSNumberProto::class.java, "toString", "Number.prototype.toString"),
    NumberProtoValueOf(JSNumberProto::class.java, "valueOf", "Number.prototype.valueOf"),

    ObjectCtorAssign(JSObjectCtor::class.java, "assign", "Object.assign"),
    ObjectCtorCreate(JSObjectCtor::class.java, "create", "Object.create"),
    ObjectCtorDefineProperties(JSObjectCtor::class.java, "defineProperties", "Object.defineProperties"),
    ObjectCtorDefineProperty(JSObjectCtor::class.java, "defineProperty", "Object.defineProperty"),
    ObjectCtorEntries(JSObjectCtor::class.java, "entries", "Object.entries"),
    ObjectCtorFreeze(JSObjectCtor::class.java, "freeze", "Object.freeze"),
    ObjectCtorFromEntries(JSObjectCtor::class.java, "fromEntries", "Object.fromEntries"),
    ObjectCtorGetOwnPropertyDescriptor(JSObjectCtor::class.java, "getOwnPropertyDescriptor", "Object.getOwnPropertyDescriptor"),
    ObjectCtorGetOwnPropertyDescriptors(JSObjectCtor::class.java, "getOwnPropertyDescriptors", "Object.getOwnPropertyDescriptors"),
    ObjectCtorGetOwnPropertyNames(JSObjectCtor::class.java, "getOwnPropertyNames", "Object.getOwnPropertyNames"),
    ObjectCtorGetOwnPropertySymbols(JSObjectCtor::class.java, "getOwnPropertySymbols", "Object.getOwnPropertySymbols"),
    ObjectCtorGetPrototypeOf(JSObjectCtor::class.java, "getPrototypeOf", "Object.getPrototypeOf"),
    ObjectCtorIs(JSObjectCtor::class.java, "is_", "Object.is"),
    ObjectCtorIsExtensible(JSObjectCtor::class.java, "isExtensible", "Object.isExtensible"),
    ObjectCtorIsFrozen(JSObjectCtor::class.java, "isFrozen", "Object.isFrozen"),
    ObjectCtorIsSealed(JSObjectCtor::class.java, "isSealed", "Object.isSealed"),
    ObjectCtorKeys(JSObjectCtor::class.java, "keys", "Object.keys"),
    ObjectCtorPreventExtensions(JSObjectCtor::class.java, "preventExtensions", "Object.preventExtensions"),
    ObjectCtorSeal(JSObjectCtor::class.java, "seal", "Object.seal"),
    ObjectCtorSetPrototypeOf(JSObjectCtor::class.java, "setPrototypeOf", "Object.setPrototypeOf"),
    ObjectCtorValues(JSObjectCtor::class.java, "values", "Object.values"),
    ObjectProtoGetProto(JSObjectProto::class.java, "getProto", "Object.prototype.getPrototype"),
    ObjectProtoSetProto(JSObjectProto::class.java, "setProto", "Object.prototype.setPrototype"),
    ObjectProtoDefineGetter(JSObjectProto::class.java, "defineGetter", "Object.prototype.defineGetter"),
    ObjectProtoDefineSetter(JSObjectProto::class.java, "defineSetter", "Object.prototype.defineSetter"),
    ObjectProtoLookupGetter(JSObjectProto::class.java, "lookupGetter", "Object.prototype.lookupGetter"),
    ObjectProtoLookupSetter(JSObjectProto::class.java, "lookupSetter", "Object.prototype.lookupSetter"),
    ObjectProtoHasOwnProperty(JSObjectProto::class.java, "hasOwnProperty", "Object.prototype.hasOwnProperty"),
    ObjectProtoIsPrototypeOf(JSObjectProto::class.java, "isPrototypeOf", "Object.prototype.isPrototypeOf"),
    ObjectProtoPropertyIsEnumerable(JSObjectProto::class.java, "propertyIsEnumerable", "Object.prototype.propertyIsEnumerable"),
    ObjectProtoToLocaleString(JSObjectProto::class.java, "toLocaleString", "Object.prototype.toLocaleString"),
    ObjectProtoToString(JSObjectProto::class.java, "toString", "Object.prototype.toString"),
    ObjectProtoValueOf(JSObjectProto::class.java, "valueOf", "Object.prototype.valueOf"),

    ObjectPropertyIteratorProtoNext(JSObjectPropertyIteratorProto::class.java, "next", "ObjectPropertyIterator.prototype.next"),

    PromiseCtorAll(JSPromiseCtor::class.java, "all", "Promise.all"),
    PromiseCtorAllSettled(JSPromiseCtor::class.java, "allSettled", "Promise.allSettled"),
    PromiseCtorResolve(JSPromiseCtor::class.java, "resolve", "Promise.resolve"),
    PromiseCtorReject(JSPromiseCtor::class.java, "reject", "Promise.reject"),
    PromiseProtoCatch(JSPromiseProto::class.java, "catch", "Promise.prototype.catch"),
    PromiseProtoFinally(JSPromiseProto::class.java, "finally", "Promise.prototype.finally"),
    PromiseProtoThen(JSPromiseProto::class.java, "then", "Promise.prototype.then"),

    ProxyCtorRevocable(JSProxyCtor::class.java, "revocable", "Proxy.revocable"),

    ReflectApply(JSReflectObject::class.java, "apply", "Reflect.apply"),
    ReflectConstruct(JSReflectObject::class.java, "construct", "Reflect.construct"),
    ReflectDefineProperty(JSReflectObject::class.java, "defineProperty", "Reflect.defineProperty"),
    ReflectDeleteProperty(JSReflectObject::class.java, "deleteProperty", "Reflect.deleteProperty"),
    ReflectGet(JSReflectObject::class.java, "get", "Reflect.get"),
    ReflectGetOwnPropertyDescriptor(JSReflectObject::class.java, "getOwnPropertyDescriptor", "Reflect.getOwnPropertyDescriptor"),
    ReflectHas(JSReflectObject::class.java, "has", "Reflect.has"),
    ReflectIsExtensible(JSReflectObject::class.java, "isExtensible", "Reflect.isExtensible"),
    ReflectOwnKeys(JSReflectObject::class.java, "ownKeys", "Reflect.ownKeys"),
    ReflectPreventExtensions(JSReflectObject::class.java, "preventExtensions", "Reflect.preventExtensions"),
    ReflectSet(JSReflectObject::class.java, "set", "Reflect.set"),
    ReflectSetPrototypeOf(JSReflectObject::class.java, "setPrototypeOf", "Reflect.setPrototypeOf"),

    RegExpCtorGetSpecies(JSRegExpCtor::class.java, "getSymbolSpecies", "RegExp[@@species]"),
    RegExpProtoGetDotAll(JSRegExpProto::class.java, "getDotAll", "RegExp.prototype.getDotAll"),
    RegExpProtoGetFlags(JSRegExpProto::class.java, "getFlags", "RegExp.prototype.getFlags"),
    RegExpProtoGetGlobal(JSRegExpProto::class.java, "getGlobal", "RegExp.prototype.getGlobal"),
    RegExpProtoGetIgnoreCase(JSRegExpProto::class.java, "getIgnoreCase", "RegExp.prototype.getIgnoreCase"),
    RegExpProtoGetMultiline(JSRegExpProto::class.java, "getMultiline", "RegExp.prototype.getMultiline"),
    RegExpProtoGetSource(JSRegExpProto::class.java, "getSource", "RegExp.prototype.getSource"),
    RegExpProtoGetSticky(JSRegExpProto::class.java, "getSticky", "RegExp.prototype.getSticky"),
    RegExpProtoGetUnicode(JSRegExpProto::class.java, "getUnicode", "RegExp.prototype.getUnicode"),
    RegExpProtoMatch(JSRegExpProto::class.java, "symbolMatch", "RegExp.prototype.match"),
    RegExpProtoMatchAll(JSRegExpProto::class.java, "symbolMatchAll", "RegExp.prototype.matchAll"),
    RegExpProtoReplace(JSRegExpProto::class.java, "symbolReplace", "RegExp.prototype.replace"),
    RegExpProtoSearch(JSRegExpProto::class.java, "symbolSearch", "RegExp.prototype.search"),
    RegExpProtoSplit(JSRegExpProto::class.java, "symbolSplit", "RegExp.prototype.split"),
    RegExpProtoExec(JSRegExpProto::class.java, "exec", "RegExp.prototype.exec"),
    RegExpProtoTest(JSRegExpProto::class.java, "test", "RegExp.prototype.test"),
    RegExpProtoToString(JSRegExpProto::class.java, "toString", "RegExp.prototype.toString"),

    RegExpStringIteratorProtoNext(JSRegExpStringIteratorProto::class.java, "next", "RegExpStringIterator.prototype.next"),

    SetCtorGetSymbolSpecies(JSSetCtor::class.java, "getSymbolSpecies", "Set[@@species]"),
    SetProtoGetSize(JSSetProto::class.java, "getSize", "Set.prototype.getSize"),
    SetProtoAdd(JSSetProto::class.java, "add", "Set.prototype.add"),
    SetProtoClear(JSSetProto::class.java, "clear", "Set.prototype.clear"),
    SetProtoDelete(JSSetProto::class.java, "delete", "Set.prototype.delete"),
    SetProtoEntries(JSSetProto::class.java, "entries", "Set.prototype.entries"),
    SetProtoForEach(JSSetProto::class.java, "forEach", "Set.prototype.forEach"),
    SetProtoHas(JSSetProto::class.java, "has", "Set.prototype.has"),
    SetProtoValues(JSSetProto::class.java, "values", "Set.prototype.values"),

    SetIteratorProtoNext(JSSetIteratorProto::class.java, "next", "SetIterator.prototype.next"),

    StringIteratorProtoNext(JSStringIteratorProto::class.java, "next", "StringIterator.prototype.next"),

    StringCtorFromCharCode(JSStringCtor::class.java, "fromCharCode", "String.fromCharCode"),
    StringCtorFromCodePoint(JSStringCtor::class.java, "fromCodePoint", "String.fromCodePoint"),
    StringProtoAt(JSStringProto::class.java, "at", "String.prototype.at"),
    StringProtoCharAt(JSStringProto::class.java, "charAt", "String.prototype.charAt"),
    StringProtoCharCodeAt(JSStringProto::class.java, "charCodeAt", "String.prototype.charCodeAt"),
    StringProtoCodePointAt(JSStringProto::class.java, "codePointAt", "String.prototype.codePointAt"),
    StringProtoConcat(JSStringProto::class.java, "concat", "String.prototype.concat"),
    StringProtoEndsWith(JSStringProto::class.java, "endsWith", "String.prototype.endsWith"),
    StringProtoIncludes(JSStringProto::class.java, "includes", "String.prototype.includes"),
    StringProtoIndexOf(JSStringProto::class.java, "indexOf", "String.prototype.indexOf"),
    StringProtoLastIndexOf(JSStringProto::class.java, "lastIndexOf", "String.prototype.lastIndexOf"),
    StringProtoPadEnd(JSStringProto::class.java, "padEnd", "String.prototype.padEnd"),
    StringProtoPadStart(JSStringProto::class.java, "padStart", "String.prototype.padStart"),
    StringProtoRepeat(JSStringProto::class.java, "repeat", "String.prototype.repeat"),
    StringProtoReplace(JSStringProto::class.java, "replace", "String.prototype.replace"),
    StringProtoSlice(JSStringProto::class.java, "slice", "String.prototype.slice"),
    StringProtoSplit(JSStringProto::class.java, "split", "String.prototype.split"),
    StringProtoStartsWith(JSStringProto::class.java, "startsWith", "String.prototype.startsWith"),
    StringProtoSubstring(JSStringProto::class.java, "substring", "String.prototype.substring"),
    StringProtoToLowerCase(JSStringProto::class.java, "toLowerCase", "String.prototype.toLowerCase"),
    StringProtoToString(JSStringProto::class.java, "toString", "String.prototype.toString"),
    StringProtoToUpperCase(JSStringProto::class.java, "toUpperCase", "String.prototype.toUpperCase"),
    StringProtoTrim(JSStringProto::class.java, "trim", "String.prototype.trim"),
    StringProtoTrimEnd(JSStringProto::class.java, "trimEnd", "String.prototype.trimEnd"),
    StringProtoTrimStart(JSStringProto::class.java, "trimStart", "String.prototype.trimStart"),
    StringProtoValueOf(JSStringProto::class.java, "valueOf", "String.prototype.valueOf"),
    StringProtoSymbolIterator(JSStringProto::class.java, "symbolIterator", "String.prototype[@@iterator]"),

    SymbolCtorFor(JSSymbolCtor::class.java, "for_", "Symbol.for"),
    SymbolCtorKeyFor(JSSymbolCtor::class.java, "keyFor", "Symbol.keyFor"),
    SymbolProtoToString(JSSymbolProto::class.java, "toString", "Symbol.prototype.toString"),
    SymbolProtoToValue(JSSymbolProto::class.java, "toValue", "Symbol.prototype.toValue"),
    SymbolProtoSymbolToPrimitive(JSSymbolProto::class.java, "symbolToPrimitive", "Symbol.prototype[@@toPrimitive]"),

    TypedArrayCtorFrom(JSTypedArrayCtor::class.java, "from", "TypedArray.from"),
    TypedArrayCtorOf(JSTypedArrayCtor::class.java, "of", "TypedArray.of"),
    TypedArrayProtoGetSymbolToStringTag(JSTypedArrayProto::class.java, "getSymbolToStringTag", "TypedArray.prototype[@@toStringTag]"),
    TypedArrayProtoGetBuffer(JSTypedArrayProto::class.java, "getBuffer", "TypedArray.prototype.getBuffer"),
    TypedArrayProtoGetByteLength(JSTypedArrayProto::class.java, "getByteLength", "TypedArray.prototype.getByteLength"),
    TypedArrayProtoGetByteOffset(JSTypedArrayProto::class.java, "getByteOffset", "TypedArray.prototype.getByteOffset"),
    TypedArrayProtoGetLength(JSTypedArrayProto::class.java, "getLength", "TypedArray.prototype.getLength"),
    TypedArrayProtoAt(JSTypedArrayProto::class.java, "at", "TypedArray.prototype.at"),
    TypedArrayProtoCopyWithin(JSTypedArrayProto::class.java, "copyWithin", "TypedArray.prototype.copyWithin"),
    TypedArrayProtoEntries(JSTypedArrayProto::class.java, "entries", "TypedArray.prototype.entries"),
    TypedArrayProtoEvery(JSTypedArrayProto::class.java, "every", "TypedArray.prototype.every"),
    TypedArrayProtoFill(JSTypedArrayProto::class.java, "fill", "TypedArray.prototype.fill"),
    TypedArrayProtoFilter(JSTypedArrayProto::class.java, "filter", "TypedArray.prototype.filter"),
    TypedArrayProtoFind(JSTypedArrayProto::class.java, "find", "TypedArray.prototype.find"),
    TypedArrayProtoFindIndex(JSTypedArrayProto::class.java, "findIndex", "TypedArray.prototype.findIndex"),
    TypedArrayProtoForEach(JSTypedArrayProto::class.java, "forEach", "TypedArray.prototype.forEach"),
    TypedArrayProtoIncludes(JSTypedArrayProto::class.java, "includes", "TypedArray.prototype.includes"),
    TypedArrayProtoIndexOf(JSTypedArrayProto::class.java, "indexOf", "TypedArray.prototype.indexOf"),
    TypedArrayProtoJoin(JSTypedArrayProto::class.java, "join", "TypedArray.prototype.join"),
    // TypedArrayProtoKeys(JSTypedArrayProto::class.java, "keys"),
    TypedArrayProtoLastIndexOf(JSTypedArrayProto::class.java, "lastIndexOf", "TypedArray.prototype.lastIndexOf"),
    // TypedArrayProtoMap(JSTypedArrayProto::class.java, "map"),
    TypedArrayProtoReduce(JSTypedArrayProto::class.java, "reduce", "TypedArray.prototype.reduce"),
    TypedArrayProtoReduceRight(JSTypedArrayProto::class.java, "reduceRight", "TypedArray.prototype.reduceRight"),
    TypedArrayProtoReverse(JSTypedArrayProto::class.java, "reverse", "TypedArray.prototype.reverse"),
    // TypedArrayProtoSet(JSTypedArrayProto::class.java, "set"),
    // TypedArrayProtoSlice(JSTypedArrayProto::class.java, "slice"),
    TypedArrayProtoSome(JSTypedArrayProto::class.java, "some", "TypedArray.prototype.some"),
    // TypedArrayProtoSort(JSTypedArrayProto::class.java, "sort"),
    // TypedArrayProtoSubarray(JSTypedArrayProto::class.java, "subarray"),
    // TypedArrayProtoToString(JSTypedArrayProto::class.java, "toString"),
    // TypedArrayProtoValues(JSTypedArrayProto::class.java, "values"),

    // Non-standard builtins

    ClassProtoToString(JSClassProto::class.java, "toString", "<JVM Class>.prototype.toString"),
    ClassToString(JSClassObject::class.java, "toString", "<JVM Class>.toString"),
    ConsoleProtoLog(JSConsoleProto::class.java, "log", "Console.prototype.log"),
    GlobalIsNaN(JSGlobalObject::class.java, "isNaN", "isNaN"),
    GlobalId(JSGlobalObject::class.java, "id", "id"),
    GlobalJvm(JSGlobalObject::class.java, "jvm", "jvm"),
    GlobalInspect(JSGlobalObject::class.java, "inspect", "inspect"),
    PackageProtoToString(JSPackageProto::class.java, "toString", "<JVM Package>.prototype.toString");

    override val handle: MethodHandle = MethodHandles.publicLookup().findStatic(clazz, name, Builtin.METHOD_TYPE)
}
