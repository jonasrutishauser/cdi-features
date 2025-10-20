package io.github.jonasrutishauser.cdi.features;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkus.arc.processor.bcextensions.BuildServicesImpl;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;

@ExtendWith(ArquillianExtension.class)
class ArcIT extends AbstractIT {

    @Override
    protected void setSelected(int selected) {
        clearCache();
        super.setSelected(selected);
    }

    @Deployment
    static JavaArchive deployment() {
        BuildServicesResolver.setBuildServices(new BuildServicesImpl());
        return ShrinkWrap.create(JavaArchive.class) //
                .addPackages(true, Feature.class.getPackage()) //
                .addPackages(true, "io.smallrye.config") //
                .addPackages(true, "org.eclipse.microprofile.config") //
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

}
