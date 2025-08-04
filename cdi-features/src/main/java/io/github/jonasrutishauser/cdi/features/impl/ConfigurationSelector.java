package io.github.jonasrutishauser.cdi.features.impl;

import static io.github.jonasrutishauser.cdi.features.impl.FeatureInstances.feature;

import java.util.Optional;

import org.eclipse.microprofile.config.Config;

import io.github.jonasrutishauser.cdi.features.ContextualSelector;
import io.github.jonasrutishauser.cdi.features.Feature;
import jakarta.inject.Inject;

class ConfigurationSelector implements ContextualSelector {

    private final Config config;

    @Inject
    ConfigurationSelector(Config config) {
        this.config = config;
    }

    @Override
    public boolean selected(Context context) {
        Feature feature = feature(context.bean()).orElseThrow();
        Optional<String> value = config.getOptionalValue(feature.propertyKey(), String.class);
        return value.filter(
                v -> feature.propertyValue().isEmpty() || v.equals(feature.propertyValue()))
                .isPresent();
    }

}
