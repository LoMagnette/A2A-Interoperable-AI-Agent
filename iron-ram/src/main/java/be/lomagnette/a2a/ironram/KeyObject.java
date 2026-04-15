package be.lomagnette.a2a.ironram;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Parameters;
import jakarta.persistence.Entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Entity
public class KeyObject extends PanacheEntity {

    public String name;
    public String location;
    public String description;


    public static List<KeyObject> findByDescriptionContainingAllWords(List<String> words) {
        List<String> ws = normalize(words);
        if (ws.isEmpty()) return List.of();

        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        for (int i = 0; i < ws.size(); i++) {
            if (i > 0) where.append(" and ");
            String param = "w" + i;
            where.append("lower(description) like :").append(param).append(" escape '\\'");
            params.and(param, "%" + escapeLike(ws.get(i)) + "%");
        }
        return find(where.toString(), params).list();
    }

    public static KeyObject findByName(String name) {
        if (name == null) return null;
        return find("name", name).firstResult();

    }

    /**
     * Optional: contains ANY word (OR logic). Keep if useful.
     */
    public static List<KeyObject> findByDescriptionContainingAnyWord(List<String> words) {
        List<String> ws = normalize(words);
        if (ws.isEmpty()) return List.of();

        StringBuilder where = new StringBuilder("(");
        Parameters params = new Parameters();
        for (int i = 0; i < ws.size(); i++) {
            if (i > 0) where.append(" or ");
            String param = "w" + i;
            where.append("lower(description) like :").append(param).append(" escape '\\'");
            params.and(param, "%" + escapeLike(ws.get(i)) + "%");
        }
        where.append(")");
        return find(where.toString(), params).list();
    }

    // --- helpers ---

    private static List<String> normalize(List<String> words) {
        if (words == null) return List.of();
        // trim, toLowerCase, remove empties & duplicates while preserving order
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String w : words) {
            if (w == null) continue;
            String t = w.trim().toLowerCase();
            if (!t.isEmpty()) unique.add(t);
        }
        return new ArrayList<>(unique);
    }

    /**
     * Escape LIKE wildcards and backslash for JPQL LIKE with "escape '\'"
     */
    private static String escapeLike(String s) {
        Objects.requireNonNull(s);
        return s
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
