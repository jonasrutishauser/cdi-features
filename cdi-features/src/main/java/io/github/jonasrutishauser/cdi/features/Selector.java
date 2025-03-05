package io.github.jonasrutishauser.cdi.features;

public interface Selector extends ContextualSelector {
    boolean selected();

    @Override
    default boolean selected(Context context) {
        return selected();
    }
}
