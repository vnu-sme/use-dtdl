package org.tzi.use.dtdl.mapping;

import org.tzi.use.api.UseModelApi;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MapperHelper {
    private MapperHelper() {}

    public static String sanitize(String id) {
        if (id == null) return null;
        String s = id.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) s = "Unnamed";
        if (Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    public static int stableHash(String s, Object fallback) {
        return s == null ? System.identityHashCode(fallback) : Math.abs(s.hashCode());
    }

    public static String multiplicityToString(Integer min, Integer max) {
        int lo = min == null ? 0 : min.intValue();
        int hi = max == null ? Integer.MAX_VALUE : max.intValue();
        if (hi == Integer.MAX_VALUE) {
            if (lo == 0) return "0..*";
            if (lo == 1) return "1..*";
            return lo + "..*";
        } else {
            if (lo == hi) return String.valueOf(lo);
            return lo + ".." + hi;
        }
    }

    public static String nonNull(String a, String b) {
        return a != null ? a : b;
    }

    public static String[] computeRoleNames(
            MClass srcCls,
            MClass tgtCls,
            String srcName,
            String tgtName,
            String relName,
            Relationship r
    ) {

        String leftRole;
        String rightRole;

        boolean selfRelation =
                Objects.equals(srcCls.name(), tgtCls.name()) ||
                        Objects.equals(srcName, tgtName);

        if (selfRelation) {
            leftRole = srcName + "_" + relName + "_From";
            rightRole = tgtName + "_" + relName + "_To";
        } else {
            leftRole = srcName + "_" + relName;
            rightRole = tgtName + "_" + relName;
        }

        int suffix = stableHash(r.getId() != null ? r.getId() : relName, r) & 0xffff;

        String finalLeftRole = leftRole;
        boolean clashLeft =
                srcCls.allAttributes().stream()
                        .anyMatch(a -> a.name().equals(finalLeftRole));

        if (clashLeft)
            leftRole = leftRole + "_" + suffix;

        String finalRightRole = rightRole;
        boolean clashRight =
                tgtCls.allAttributes().stream()
                        .anyMatch(a -> a.name().equals(finalRightRole));

        if (clashRight)
            rightRole = rightRole + "_" + suffix;

        return new String[]{leftRole, rightRole};
    }

    public static String[] computeBidirectionalRoleNames(MClass srcCls, MClass tgtCls, String leftRole, String rightRole) {
        return new String[]{srcCls + "_" + leftRole, tgtCls + "_" + rightRole};
    }

    public static String buildAssociationName(
            MModel model,
            String srcName,
            String tgtName,
            String relName,
            Relationship r,
            Object fallback
    ) {
        String assocBase = sanitize(srcName + "_" + relName + "_" + tgtName);

        String assocName = assocBase;

        while (model.getAssociation(assocName) != null ||
                model.getAssociationClass(assocName) != null) {

            assocName = assocBase + "_"
                    + stableHash(nonNull(r.getId(), relName) + assocName, fallback);
        }

        return assocName;
    }
}
