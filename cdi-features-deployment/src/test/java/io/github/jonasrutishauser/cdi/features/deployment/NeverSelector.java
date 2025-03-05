package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.ThrowableSelector;

public class NeverSelector implements ThrowableSelector {
    @Override
    public void valid() throws Exception {
        throw new IllegalStateException("never");
    }
}