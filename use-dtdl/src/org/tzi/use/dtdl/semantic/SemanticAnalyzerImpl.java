package org.tzi.use.dtdl.semantic;

import org.tzi.use.dtdl.ast.ASTInterface;
import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.ast.Converter;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;

import java.util.*;

public class SemanticAnalyzerImpl implements SemanticAnalyzer {
    private final DTDLModelRegistry registry;
    private final DTDLContext ctx;

    public SemanticAnalyzerImpl() {
        this(new DTDLModelRegistry());
    }

    public SemanticAnalyzerImpl(DTDLModelRegistry registry) {
        this.registry = registry == null ? new DTDLModelRegistry() : registry;
        this.ctx = new DTDLContext(this.registry);
    }

    @Override
    public DTDLContext getContext() {
        return ctx;
    }

    public DTDLModelRegistry getRegistry() {
        return registry;
    }

    @Override
    public DTDLModel analyze(List<ASTInterface> astInterfaces) {
        if (astInterfaces == null) return null;


        // clear per-run AST state so context can be reused across loads
        ctx.interfaces.clear();
        ctx.schemas.clear();
        ctx.errors.clear();

        // Phase 0: register interface ids so cross-refs can be validated during validate()
        for (ASTInterface iface : astInterfaces) {
            Object idObj = iface.props.get("@id");
            if (idObj instanceof String id) {
                // early registration (placeholder) so components/relationships can reference it
                ctx.registerInterface(id, iface);
            }
        }

        // Phase 1: resolve AST internals (schemas, inline objects, fill fields)
        for (ASTInterface iface : astInterfaces) {
            try {
                iface.resolveAll();
//                iface.printsAll();
            } catch (Exception ex) {
                // keep going but record error
                Object maybeId = iface.props.get("@id");
                String loc = maybeId instanceof String ? (String) maybeId : null;
                ctx.report("Resolver error for interface: " + maybeId + " - " + ex.getMessage(), loc);
            }
        }



        // Phase 1b: register top-level schemas declared by the AST interfaces
        for (ASTInterface iface : astInterfaces) {
            try {
                for (Map.Entry<String, ASTSchema> e : iface.schemaIndex.entrySet()) {
                    String sid = e.getKey();
                    ASTSchema astSchema = e.getValue();
                    if (sid == null || astSchema == null) continue;

                    ctx.registerSchema(sid, astSchema);
                }
            } catch (Exception ex) {
                Object maybeId = iface.props.get("@id");
                String loc = maybeId instanceof String ? (String) maybeId : null;
                ctx.report(
                        "Schema registration error for interface: " + maybeId + " - " + ex.getMessage(),
                        loc
                );
            }
        }

        // Detect cycles among AST interfaces in the current analyze pass:
        // simple DFS using ctx.interfaces map (only local AST graph)
        // We only check cycles that involve only AST interfaces (registered models can't form AST cycles).
        validateInheritanceGraph();
        // limit depth of extends chain to avoid pathological deep inheritance
        validateInheritanceDepth();

        // Phase 2: semantic validate each interface (each AST class validates its own children)
        for (ASTInterface iface : astInterfaces) {
            try {
                iface.validate(this);
            } catch (Exception ex) {
                Object maybeId = iface.props.get("@id");
                String loc = maybeId instanceof String ? (String) maybeId : null;
                ctx.report("Validation exception for interface " + maybeId + ": " + ex.getMessage(), loc);
            }
        }

        // If errors present, return null (caller inspects ctx.errors)
        if (!ctx.errors.isEmpty()) {
            return null;
        }

        // Phase 3: convert AST -> DTDLModel
        try {
            Converter conv = new Converter(ctx);
            return conv.buildModel(astInterfaces);
        } catch (Exception ex) {
            ctx.report("Conversion to DTDLModel failed: " + ex.getMessage());
            return null;
        }
    }

    private void validateInheritanceGraph() {
        // 1. Validate non-existence interface
        Map<String, List<String>> graph = new HashMap<>(); // InterfaceID -> List of InterfaceIDs it extends

        for (Map.Entry<String, ASTInterface> e : ctx.interfaces.entrySet()) {
            ASTInterface ai = e.getValue();
            Object ex = ai.props.get("extends");

            List<String> exts = new ArrayList<>();
            if (ex instanceof List<?>) {
                for (Object o : (List<?>) ex) {
                    if (o instanceof String s) {
                        if (ctx.interfaces.containsKey(s)) { // this ifaceId exists in current context --> valid
                            exts.add(s);
                        } else if (ctx.getModelForInterface(s) == null) {
                            ctx.report("Interface extends unknown interface: " + s, e.getKey());
                        }
                    }
                }
            }
            graph.put(e.getKey(), exts); // if all extend ids valid --> store in graph
        }


        // 2. Cycle detection
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (String id : graph.keySet()) {
            dfsCycle(id, graph, visiting, visited);
        }
    }

    private void dfsCycle(String node, Map<String, List<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) {
            ctx.report("Inheritance cycle detected involving interface: " + node, node);
            return;
        }
        if (visited.contains(node)) return;

        visiting.add(node);
        for (String nxt : graph.getOrDefault(node, List.of())) { // visit all parent nodes
            dfsCycle(nxt, graph, visiting, visited);
        }
        visiting.remove(node);
        visited.add(node);
    }

    private void validateInheritanceDepth() {
        Map<String, Integer> cache = new HashMap<>();

        for (String id : ctx.interfaces.keySet()) {
            int depth = computeDepth(id, cache, new HashSet<>());
            if (depth > 10) {
                ctx.report(
                        "Inheritance depth " + depth +
                                " exceeds maximum allowed (" + 10 + ")",
                        id
                );
            }
        }
    }

    private int computeDepth(String id, Map<String, Integer> cache, Set<String> visiting) {
        if (cache.containsKey(id)) return cache.get(id);
        if (visiting.contains(id)) return 0;

        visiting.add(id);
        ASTInterface iface = ctx.interfaces.get(id);
        int max = 1;

        if (iface != null) {
            Object ex = iface.props.get("extends");
            if (ex instanceof List<?>) {
                for (Object o : (List<?>) ex) {
                    if (o instanceof String s && ctx.interfaces.containsKey(s)) {
                        max = Math.max(max, 1 + computeDepth(s, cache, visiting));
                    }
                }
            }
        }

        visiting.remove(id);
        cache.put(id, max);
        return max;
    }
}
