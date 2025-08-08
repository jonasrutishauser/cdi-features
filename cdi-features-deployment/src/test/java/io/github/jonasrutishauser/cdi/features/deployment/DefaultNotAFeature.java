package io.github.jonasrutishauser.cdi.features.deployment;

import jakarta.enterprise.context.Dependent;

@Dependent
class DefaultNotAFeature implements NotAFeature {
    @Override
    public CharSequence test() {
        return "default";
    }
}
