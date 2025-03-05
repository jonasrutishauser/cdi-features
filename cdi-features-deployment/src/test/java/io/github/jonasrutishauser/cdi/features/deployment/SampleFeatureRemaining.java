package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Feature(remaining = true)
class SampleFeatureRemaining implements SampleFeature {
    @Override
    public String test() {
        return "SampleFeatureRemaining";
    }
}