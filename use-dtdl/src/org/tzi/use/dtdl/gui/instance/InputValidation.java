package org.tzi.use.dtdl.gui.instance;

/**
 * Helper sentinel for input extraction:
 * - If panels return InputValidation.INVALID, caller must treat as validation error (not empty).
 * - If panels return null, treat as empty/absent.
 */
public final class InputValidation {
    public static final Object INVALID = new Object();

    private InputValidation() {}
}
