package org.tzi.use.dtdl.semantic;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.ast.ASTBidirectionalRelationship;
import org.tzi.use.dtdl.ast.ASTInterface;
import org.tzi.use.dtdl.ast.ASTRelationship;
import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.ast.Converter;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;

import java.sql.SQLOutput;
import java.util.*;

public class SemanticAnalyzerImpl implements SemanticAnalyzer {
    private static final int MAX_INHERITANCE_DEPTH = 10;
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
        ctx.errors.clear();
        ctx.clearWarnings();

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

        // Detect cycles among AST interfaces in the current analyze pass:
        // simple DFS using ctx.interfaces map (only local AST graph)
        // We only check cycles that involve only AST interfaces (registered models can't form AST cycles).
        validateInheritanceGraph();

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

        // Phase 3: Detect and merge bidirectional relationships
        validateBiDirectionalRelationship();

        // Phase 4: convert AST -> DTDLModel
        try {
            Converter conv = new Converter(ctx);
            return conv.buildModel(astInterfaces);
        } catch (Exception ex) {
            ctx.report("Conversion to DTDLModel failed: " + ex.getMessage());
            return null;
        }
    }

    private void validateBiDirectionalRelationship() {
        Map<String, ASTRelationship> directedRelationships = new HashMap<>();
        Set<ASTRelationship> relationshipsToRemove = new HashSet<>();

        for (Map.Entry<String, ASTInterface> e : ctx.interfaces.entrySet()) {
            String sourceId = e.getKey();
            ASTInterface sourceInterface = e.getValue();

            for (ASTRelationship rel : sourceInterface.getRelationships()) {

                if (rel.targetInterface == null) {
                    continue;
                }

                ASTInterface targetInterface = ctx.interfaces.get(rel.targetInterface);

                if (targetInterface == null) {
                    continue;
                }

                String targetId = targetInterface.id;

                String key = sourceId + "->" + targetId;
                String reverseKey = targetId + "->" + sourceId;

                // canonical pair key
                String pairKey = sourceId.compareTo(targetId) < 0
                        ? sourceId + "<->" + targetId
                        : targetId + "<->" + sourceId;


                ASTRelationship reverseRel = directedRelationships.get(reverseKey);

                // no reverse yet
                if (reverseRel == null) {
                    directedRelationships.put(key, rel);
                    continue;
                }

                ctx.reportWarning("Bidirectional relationship detected: " + pairKey);

                ASTBidirectionalRelationship merged = new ASTBidirectionalRelationship(rel.id, rel.name, rel.getComment(), rel.description, rel.getDisplayName(),
                        rel.getType(), reverseRel.name);

                merged.targetInterface = rel.targetInterface;
                merged.minMultiplicity = rel.minMultiplicity;
                merged.maxMultiplicity = rel.maxMultiplicity;
                merged.targetMinMultiplicity = reverseRel.minMultiplicity;
                merged.targetMaxMultiplicity = reverseRel.maxMultiplicity;
                merged.writable = rel.writable;

                sourceInterface.addBidirectionalRelationship(merged);
                relationshipsToRemove.add(reverseRel);
                relationshipsToRemove.add(rel);
            }
        }

        // apply removals once
        for (ASTInterface iface : ctx.interfaces.values()) {
            iface.getRelationships().removeIf(relationshipsToRemove::contains);
        }
    }

    private void validateInheritanceGraph() {
        // Build graph (local AST interfaces only)
        Map<String, List<String>> graph = new HashMap<>();

        for (Map.Entry<String, ASTInterface> e : ctx.interfaces.entrySet()) {
            String ifaceId = e.getKey();
            ASTInterface ai = e.getValue();

            LinkedHashSet<String> parents = new LinkedHashSet<>();
            for (String raw : ai.getExtendsList()) {
                if (raw == null) continue;
                String p = raw.trim();
                if (p.isEmpty()) continue;
                if (p.equals(ifaceId)) {
                    ctx.report("Interface '" + ifaceId + "' extends itself", ifaceId);
                    continue;
                }
                parents.add(p);
            }

            List<String> exts = new ArrayList<>();
            for (String p : parents) {
                // If parent is defined in this AST set -> keep as graph edge
                if (ctx.interfaces.containsKey(p)) {
                    exts.add(p);
                } else {
                    // Not in local AST: consult registry / canonical models
                    if (ctx.getModelForInterface(p) == null && ctx.getInterfaceFromModels(p) == null) {
                        ctx.report("Interface '" + ifaceId + "' extends unknown interface: " + p, ifaceId);
                    }
                }
            }

            graph.put(ifaceId, exts);
        }

        // Cycle detection with full path reporting
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();

        for (String id : graph.keySet()) {
            if (!visited.contains(id)) {
                dfsCycle(id, graph, visiting, visited, stack);
            }
        }
    }

    private void dfsCycle(String node, Map<String, List<String>> graph, Set<String> visiting, Set<String> visited, Deque<String> stack) {
        if (visiting.contains(node)) {
            // produce cycle path
            List<String> cycle = new ArrayList<>();
            Iterator<String> it = stack.iterator();
            boolean collecting = false;
            while (it.hasNext()) {
                String n = it.next();
                if (n.equals(node)) collecting = true;
                if (collecting) cycle.add(n);
            }
            cycle.add(node); // close the cycle
            String path = String.join(" -> ", cycle);
            ctx.report("Inheritance cycle detected: " + path, node);
            return;
        }

        if (visited.contains(node)) return;

        visiting.add(node);
        stack.push(node);
        for (String nxt : graph.getOrDefault(node, List.of())) {
            dfsCycle(nxt, graph, visiting, visited, stack);
        }
        stack.pop();
        visiting.remove(node);
        visited.add(node);
    }
}
