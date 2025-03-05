package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.enterprise.context.Dependent;

@Dependent
@Feature(selector = NeverSelector.class)
class SampleFeatureNever implements SampleFeature {
    @Override
    public String test() {
        return "SampleFeature3";
    }
}