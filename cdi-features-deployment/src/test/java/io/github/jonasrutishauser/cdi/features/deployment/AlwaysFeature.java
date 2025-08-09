package io.github.jonasrutishauser.cdi.features.deployment;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.ThrowableSelector;
import jakarta.enterprise.context.Dependent;

@Dependent
@Feature
class AlwaysFeature implements GenericSampleFeature<StringBuffer>, ThrowableSelector {
    static AtomicInteger counter = new AtomicInteger();
    private final int id;

    public AlwaysFeature() {
        this.id = counter.getAndIncrement();
    }

    @Override
    public StringBuffer test() {
        return new StringBuffer("always " + id);
    }

    @Override
    public void valid() {
        // should always be valid
    }
}
