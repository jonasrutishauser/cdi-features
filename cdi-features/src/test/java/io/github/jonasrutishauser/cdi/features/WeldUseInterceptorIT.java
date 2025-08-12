package io.github.jonasrutishauser.cdi.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jboss.weld.lite.extension.translator.BuildServicesImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junitpioneer.jupiter.SetSystemProperty;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;
import jakarta.enterprise.util.TypeLiteral;

@EnableWeld
@SetSystemProperty(key = "io.github.jonasrutishauser.cdi.features.useInterceptor", value = "true")
class WeldUseInterceptorIT extends AbstractIT {

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

    @RepeatedTest(3)
    public void delegatesScope() {
        AlwaysFeature.counter.set(0); // reset counter
        @SuppressWarnings("serial")
        Instance<GenericSampleFeature<StringBuffer>> select = instance.select(new TypeLiteral<GenericSampleFeature<StringBuffer>>() {});

        assertEquals("always 0", select.get().test().toString());
        assertEquals("always 1", select.get().test().toString());
    }

}
