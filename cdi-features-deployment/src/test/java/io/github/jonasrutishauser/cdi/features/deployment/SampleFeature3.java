package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.enterprise.context.Dependent;

@Dependent
@Feature(propertyKey = "feature", propertyValue = "3")
class SampleFeature3 implements SampleFeature {
    @Override
    public String test() {
        return "SampleFeature3";
    }
}