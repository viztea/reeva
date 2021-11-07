package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.DestructuringDeclaration
import com.reevajs.reeva.ast.statements.NamedDeclaration
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.other.JSModuleNamespaceObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.expect

@ECMAImpl("16.2.1.6", name = "Source Text Module Records")
class SourceTextModuleRecord(realm: Realm, val parsedSource: ParsedSource) : CyclicModuleRecord(realm) {
    override val requestedModules = parsedSource.node.let {
        expect(it is ModuleNode)
        it.requestedModules()
    }

    override fun execute(): JSValue {
        link()
        return evaluate().also {
            if (evaluationError != null)
                throw evaluationError!!
        }
    }

    override fun initializeEnvironment() {
        env = ModuleEnvRecord(realm.globalEnv)

        val node = parsedSource.node as ModuleNode

        // For each import, we have to tie the identifier to the module which it comes
        // from.
        node.body.filterIsInstance<ImportDeclarationNode>().forEach { decl ->
            val requestedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, decl.moduleName)

            // Note that we have to store the ModuleRecord in the indirect binding
            // instead of directly storing the ModuleEnvRecord. This is because the
            // requestedModules' env field may not have been initialized due to
            // circular (but legal) imports.
            decl.imports?.forEach {
                when (it) {
                    is DefaultImport ->
                        env.setIndirectBinding(DEFAULT_SPECIFIER, DEFAULT_SPECIFIER, requestedModule)
                    is NamespaceImport ->
                        env.setBinding(NAMESPACE_SPECIFIER, requestedModule.getNamespaceObject())
                    is NormalImport -> env.setIndirectBinding(
                        it.identifierNode.processedName,
                        it.alias.processedName,
                        requestedModule,
                    )
                }
            }
        }

        // For each export, we need to resolve the specifier and set the appropriate
        // binding in the ModuleEnvRecord to JSEmpty. JSEmpty is used as a placeholder
        // for a binding that has not been initialized. If the runtime ever encounters
        // an import which is JSEmpty, it is a sign of circularly-dependent imports,
        // and an error is thrown accordingly.
        node.body.filterIsInstance<ExportNode>().forEach { export ->
            when (export) {
                is DefaultClassExportNode,
                is DefaultExpressionExportNode,
                is DefaultFunctionExportNode -> env.setBinding(DEFAULT_SPECIFIER, JSEmpty)
                is NamedExport ->
                    env.setBinding(export.alias?.processedName ?: export.identifierNode.processedName, JSEmpty)
                is NamedExports -> export.exports.forEach {
                    env.setBinding(it.alias?.processedName ?: it.identifierNode.processedName, JSEmpty)
                }
                is DeclarationExportNode -> export.declaration.declarations.flatMap { it.names() }.forEach {
                    env.setBinding(it, JSEmpty)
                }
                is ExportFromNode -> { /* handled in next loop */ }
            }
        }

        // Export from nodes are a bit special. Since they simply re-export values from a
        // different modules, they're treated more like imports. The environment is initialized
        // with indirect imports for each export from node.
        node.body.filterIsInstance<ExportFromNode>().forEach { export ->
            val requestedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, export.moduleName)

            when (export) {
                is ExportAllAsFromNode -> env.setBinding(
                    export.identifierNode.processedName,
                    requestedModule.getNamespaceObject(),
                )
                is ExportAllFromNode -> requestedModule.getExportedNames().forEach {
                    env.setIndirectBinding(it, it, requestedModule)
                }
                is ExportNamedFromNode -> export.exports.exports.forEach {
                    env.setIndirectBinding(
                        it.alias?.processedName ?: it.identifierNode.processedName,
                        it.identifierNode.processedName,
                        requestedModule
                    )
                }
            }
        }
    }

    override fun getImportedNames(specifier: String): Set<String> {
        val node = parsedSource.node as ModuleNode
        val modules = node.body.filterIsInstance<ImportDeclarationNode>().filter {
            it.moduleName == specifier
        }

        return modules.flatMap { module ->
            module.imports?.map {
                when (it) {
                    is DefaultImport -> DEFAULT_SPECIFIER
                    is NamespaceImport -> NAMESPACE_SPECIFIER
                    is NormalImport -> it.identifierNode.processedName
                }
            } ?: emptyList()
        }.toSet()
    }

    override fun getExportedNames(): List<String> {
        val node = parsedSource.node as ModuleNode
        val names = mutableListOf<String>()

        node.body.filterIsInstance<ExportNode>().forEach { export ->
            when (export) {
                is DefaultClassExportNode,
                is DefaultExpressionExportNode,
                is DefaultFunctionExportNode -> names.add(DEFAULT_SPECIFIER)
                is ExportAllAsFromNode -> names.add(export.identifierNode.processedName)
                is ExportAllFromNode -> {
                    val requiredModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, export.moduleName)
                    names.addAll(requiredModule.getExportedNames())
                }
                is ExportNamedFromNode -> names.addAll(export.exports.exports.map {
                    it.alias?.processedName ?: it.identifierNode.processedName
                })
                is NamedExport -> names.add(export.alias?.processedName ?: export.identifierNode.processedName)
                is NamedExports -> names.addAll(export.exports.map {
                    it.alias?.processedName ?: it.identifierNode.processedName
                })
                is DeclarationExportNode -> names.addAll(export.declaration.declarations.flatMap { it.names() })
            }
        }

        return names
    }

    override fun makeNamespaceImport() = JSModuleNamespaceObject.create(
        realm,
        this,
        getExportedNames(),
    )

    override fun executeModule() {
        val sourceInfo = parsedSource.sourceInfo
        expect(sourceInfo.isModule)
        val transformedSource = Executable.transform(parsedSource)
        val function = NormalInterpretedFunction.create(realm, transformedSource, env)
        Operations.call(realm, function, realm.globalObject, emptyList())
    }

    companion object {
        fun parseModule(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, ModuleRecord> {
            return Parser(sourceInfo).parseModule().mapValue { result ->
                SourceTextModuleRecord(realm, result)
            }
        }
    }
}