package io.github.jonasrutishauser.cdi.features;

import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jboss.weld.lite.extension.translator.BuildServicesImpl;
import org.junit.jupiter.api.BeforeAll;

import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;

@EnableWeld
class WeldIT extends AbstractIT {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.performDefaultDiscovery();

    @BeforeAll
    static void setWeldBuildServices() {
        try {
            Initializer.setWeldBuildServices();
        } catch (NoClassDefFoundError e) {
            // ignore
        }
    }

    static interface Initializer {
        static void setWeldBuildServices() {
            BuildServicesResolver.setBuildServices(new BuildServicesImpl());
        }
    }

}
