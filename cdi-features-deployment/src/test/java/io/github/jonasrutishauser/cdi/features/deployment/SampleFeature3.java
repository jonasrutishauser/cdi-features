package io.github.jonasrutishauser.cdi.features.deployment;

import io.github.jonasrutishauser.cdi.features.Feature;
import io.github.jonasrutishauser.cdi.features.Feature.Cache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Feature(propertyKey = "feature", propertyValue = "3", cache = @Cache(durationMillis = 0))
class SampleFeature3 implements SampleFeature {

    @Inject
    SampleFeature3(Config config) {
        config.setFeature3Created();
    }

    @Override
    public String test() {
        return "SampleFeature3";
    }
}