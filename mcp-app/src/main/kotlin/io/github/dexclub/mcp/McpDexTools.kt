package io.github.dexclub.mcp

import io.github.dexclub.core.app.contract.ClassHit
import io.github.dexclub.core.app.contract.MethodHit
import io.modelcontextprotocol.kotlin.sdk.server.Server

internal fun McpApp.registerDexTools(server: Server) {
    registerCatalogTool(server, McpDexToolCatalog.require("inspect_method")) { request ->
        runToolCatching {
            val includes = request.methodIncludeSections()
            val target = request.dexToolTarget()
            val brief = request.briefFlag()
            val execution = inspectMethodExecution(
                sessionId = target.sessionId,
                workdir = target.workdir,
                methodHandle = request.optionalStringArgument("method_handle"),
                descriptor = request.optionalStringArgument("descriptor"),
                sourcePath = request.optionalStringArgument("source_path"),
                sourceEntry = request.optionalStringArgument("source_entry"),
                includes = includes,
            )
            inspectMethodResult(execution, brief)
        }
    }

    registerCatalogTool(server, McpDexToolCatalog.require("export_method_java")) { request ->
        exportMethodTextTool(request = request, view = "java")
    }
    registerCatalogTool(server, McpDexToolCatalog.require("export_method_smali")) { request ->
        exportMethodTextTool(request = request, view = "smali")
    }
    registerCatalogTool(server, McpDexToolCatalog.require("export_class_java")) { request ->
        exportClassTextTool(request = request, view = "java")
    }
    registerCatalogTool(server, McpDexToolCatalog.require("export_class_smali")) { request ->
        exportClassTextTool(request = request, view = "smali")
    }

    registerPreflightedCatalogTool(server, McpDexToolCatalog.require("find_classes"), preflight = { request ->
        loadQueryFile(request, expectedKind = "find_classes")
    }) { request, query ->
        runToolCatching {
            val target = request.dexToolTarget()
            val execution = findClassesExecution(
                sessionId = target.sessionId,
                workdir = target.workdir,
                query = query,
                offset = request.findOffset(),
                limit = request.findLimit(),
            )
            findClassesResult(
                execution = execution,
                handleProvider = execution.session?.let { activeSession ->
                    { hit: ClassHit ->
                        sessionStore.putClassHandle(activeSession.sessionId, hit.className, hit.sourcePath, hit.sourceEntry)
                    }
                },
                fields = request.classProjectionFields(target.sessionId),
                brief = request.briefFlag(),
            )
        }
    }

    registerPreflightedCatalogTool(server, McpDexToolCatalog.require("find_methods"), preflight = { request ->
        loadQueryFile(request, expectedKind = "find_methods")
    }) { request, query ->
        runToolCatching {
            val target = request.dexToolTarget()
            val execution = findMethodsExecution(
                sessionId = target.sessionId,
                workdir = target.workdir,
                query = query,
                offset = request.findOffset(),
                limit = request.findLimit(),
            )
            findMethodsResult(
                execution = execution,
                handleProvider = execution.session?.let { activeSession ->
                    { hit: MethodHit ->
                        sessionStore.putMethodHandle(activeSession.sessionId, hit.descriptor, hit.sourcePath, hit.sourceEntry)
                    }
                },
                fields = request.methodProjectionFields(target.sessionId),
                brief = request.briefFlag(),
            )
        }
    }

    registerPreflightedCatalogTool(server, McpDexToolCatalog.require("find_fields"), preflight = { request ->
        loadQueryFile(request, expectedKind = "find_fields")
    }) { request, query ->
        runToolCatching {
            val target = request.dexToolTarget()
            val execution = findFieldsExecution(
                sessionId = target.sessionId,
                workdir = target.workdir,
                query = query,
                offset = request.findOffset(),
                limit = request.findLimit(),
            )
            findFieldsResult(
                execution = execution,
                fields = request.fieldProjectionFields(),
                brief = request.briefFlag(),
            )
        }
    }
}
