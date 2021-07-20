package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.transformer.FunctionOpcodes

interface Pass {
    fun evaluate(opcodes: FunctionOpcodes)

    object OptimizationPipeline : Pass {
        override fun evaluate(opcodes: FunctionOpcodes) {
            RemoveHandlers.evaluate(opcodes)
            GenerateCFG.evaluate(opcodes)
            MergeBlocks.evaluate(opcodes)
            RemoveHandlers.evaluate(opcodes)
            GenerateCFG.evaluate(opcodes)
            PlaceBlocks.evaluate(opcodes)


            // IrPrinter(FunctionInfo("whatever", opcodes, false, false, false, null)).print()

            // TODO: This corrupts registers in loop contexts (i.e. it doesn't know how to
            // track register liveness across back-edges)

            // RegisterAllocation().evaluate(info)

            // RegisterAllocation2().evaluate(info)
        }
    }
}