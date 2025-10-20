package io.github.jonasrutishauser.cdi.features.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.cdi-features")
public interface CdiFeaturesConfiguration {
    /**
     * Wether to use an interceptor to select the feature implementation (will use a
     * special scope otherwise, which behaves like the application scope).
     */
    @WithDefault("true")
    boolean useInterceptor();
}
