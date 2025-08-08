package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.ThrowableSelector;
import jakarta.enterprise.context.Dependent;

@Dependent
@Feature
class AlwaysFeature implements GenericSampleFeature<StringBuffer>, ThrowableSelector {
    @Override
    public StringBuffer test() {
        return new StringBuffer("always");
    }

    @Override
    public void valid() {
        // should always be valid
    }
}
