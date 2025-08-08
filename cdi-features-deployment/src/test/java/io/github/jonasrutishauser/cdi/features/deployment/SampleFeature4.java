package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Feature(propertyKey = "feature4")
class SampleFeature4 implements SampleFeature {
    @Override
    public String test() {
        return "SampleFeature4";
    }
}
