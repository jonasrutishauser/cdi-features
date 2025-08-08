package io.github.jonasrutishauser.cdi.features.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.enterprise.context.Dependent;

@Dependent
@Feature(selector = NeverSelector.class)
@SuppressWarnings("rawtypes")
class NeverFeature implements GenericSampleFeature<StringBuilder>, ContextualSelector {
    @Override
    public StringBuilder test() {
        return fail("should not be called");
    }

    @Override
    public boolean selected(Context context) {
        return false;
    }
}
