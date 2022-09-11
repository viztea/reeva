package com.reevajs.reeva.transformer

import com.reevajs.reeva.transformer.opcodes.*

class IRPrinter(private val transformedSource: TransformedSource) {
    private fun printInfo(info: FunctionInfo) {
        val header = buildString {
            append("=== ")
            append(info.name)
            append(" ===")
        }

        println(header)
        println("Parameter count: ${info.ir.argCount}")
        println("Local count: ${info.ir.locals.size}")
        println("Opcode count: ${info.ir.opcodes.size}")

        println("Opcodes:")
        for ((index, opcode) in info.ir.opcodes.withIndex()) {
            print("  ")
            print("%3d".format(index))
            print(".  ")

            print(opcode::class.simpleName)

            when (opcode) {
                is Call -> println(" ${opcode.argCount}")
                is Construct -> println(" ${opcode.argCount}")
                is CreateAsyncClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateAsyncGeneratorClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateMethod -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateGeneratorClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is DeclareGlobalVars -> {
                    print(" ")
                    if (opcode.vars.isNotEmpty()) {
                        print("var={")
                        print(opcode.vars.joinToString(separator = " "))
                        print("} ")
                    }
                    if (opcode.lexs.isNotEmpty()) {
                        print("lex={")
                        print(opcode.lexs.joinToString(separator = " "))
                        print("} ")
                    }
                    println()
                }
                is DeclareGlobalFunc -> print(" ${opcode.name}")
                is LoadNamedProperty -> println(" \"${opcode.name}\"")
                is IncInt -> println(" [${opcode.local}]")
                is JumpTable -> {
                    val entries = opcode.table.entries.joinToString(separator = ", ") { (phase, target) ->
                        "$phase: @$target"
                    }
                    println(" { $entries }")
                }
                is JumpInstr -> println(" @${opcode.to}")
                is LoadCurrentEnvSlot -> println(" [${opcode.slot}]")
                is LoadEnvSlot -> println(" [${opcode.slot}] #${opcode.distance}")
                is LoadGlobal -> println(" \"${opcode.name}\"")
                is LoadInt -> println(" [${opcode.local}]")
                is LoadValue -> println(" [${opcode.local}]")
                is PushConstant -> {
                    if (opcode.literal is String) {
                        println(" \"${opcode.literal}\"")
                    } else println(" ${opcode.literal}")
                }
                is PushDeclarativeEnvRecord -> println(" #${opcode.slotCount}")
                is SetGeneratorPhase -> println(" #${opcode.phase}")
                is StoreNamedProperty -> println(" \"${opcode.name}\"")
                is StoreCurrentEnvSlot -> println(" [${opcode.slot}]")
                is StoreEnvSlot -> println(" [${opcode.slot}] #${opcode.distance}")
                is StoreGlobal -> println(" \"${opcode.name}\"")
                is StoreInt -> println(" [${opcode.local}]")
                is StoreValue -> println(" [${opcode.local}]")
                is ThrowConstantReassignmentError -> println(" \"${opcode.name}\"")
                is ThrowLexicalAccessError -> println(" \"${opcode.name}\"")
                is StoreArray -> println(" [${opcode.arrayLocal.value}] [${opcode.indexLocal.value}]")
                is StoreArrayIndexed -> println(" [${opcode.arrayLocal}] #${opcode.index}")
                is CopyObjectExcludingProperties -> println(" #${opcode.propertiesLocal}")
                is PushJVMInt -> println(" #${opcode.int}")
                is LoadModuleVar -> println(" \"${opcode.name}\"")
                is StoreModuleVar -> println(" \"${opcode.name}\"")
                is CreateTemplateLiteral -> println(" #${opcode.numberOfParts}")
                else -> println()
            }
        }

        if (info.ir.handlers.isNotEmpty()) {
            println("\nHandlers:")

            for (handler in info.ir.handlers)
                println("    ${handler.start}-${handler.end}: ${handler.handler}")
        }

        for (nestedFunction in info.ir.nestedFunctions) {
            println("\n")
            printInfo(nestedFunction)
        }
    }

    fun print() {
        printInfo(transformedSource.functionInfo)
    }
}
