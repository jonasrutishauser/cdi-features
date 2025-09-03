package io.github.jonasrutishauser.cdi.features.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class QuarkusScopeIT extends AbstractQuarkusIT {

    @RegisterExtension
    static final QuarkusUnitTest quarkusConfig = createQuarkusConfig()
            .overrideConfigKey("quarkus.cdi-features.use-interceptor", "false");

}
