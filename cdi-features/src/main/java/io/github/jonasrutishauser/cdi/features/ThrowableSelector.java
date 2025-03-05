package io.github.jonasrutishauser.cdi.features;

public interface ThrowableSelector extends Selector {
    void valid() throws Exception;

    default boolean selected() {
        try {
            valid();
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("Not valid", e);
        }
    }
}
