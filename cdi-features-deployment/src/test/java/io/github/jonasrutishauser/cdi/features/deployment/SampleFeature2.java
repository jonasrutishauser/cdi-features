package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Feature.Cache;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Feature(selector = SampleFeature2Selector.class, cache = @Cache(durationMillis = -1))
class SampleFeature2 implements SampleFeature {
    @Override
    public String test() {
        return "SampleFeature2";
    }
}