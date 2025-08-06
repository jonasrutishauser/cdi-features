package io.github.jonasrutishauser.cdi.features;

public interface Selector extends ContextualSelector<Object> {
    boolean selected();

    @Override
    default boolean selected(Context<?> context) {
        return selected();
    }
}
