package org.tzi.use.dtdl.semantic;

public final class SemanticError {
    public final String message;
    public final String location;

    public SemanticError(String message, String location) {
        this.message = message;
        this.location = location;
    }

    public SemanticError(String message) {
        this(message, null);
    }

    @Override
    public String toString() {
        return (location != null ? ("[" + location + "] ") : "") + message;
    }
}
