package io.github.jonasrutishauser.cdi.features;

import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.AddPackages;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.jboss.weld.lite.extension.translator.BuildServicesImpl;
import org.junit.jupiter.api.BeforeAll;

import io.github.jonasrutishauser.cdi.features.impl.FeaturesExtension;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;

@EnableAutoWeld
@AddExtensions({FeaturesExtension.class, ConfigExtension.class})
@AddPackages(WeldIT.class)
class WeldIT extends AbstractIT {

    @BeforeAll
    static void setWeldBuildServices() {
        try {
            Initializer.setWeldBuildServices();
        } catch (NoClassDefFoundError e) {
            // ignore
        }
    }

    static class Initializer {
        static void setWeldBuildServices() {
            BuildServicesResolver.setBuildServices(new BuildServicesImpl());
        }
    }

}
