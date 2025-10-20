package io.github.jonasrutishauser.cdi.features.deployment;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;

import io.github.jonasrutishauser.cdi.features.AbstractIT;
import io.quarkus.test.QuarkusUnitTest;

abstract class AbstractQuarkusIT extends AbstractIT {

    static QuarkusUnitTest createQuarkusConfig() {
        return new QuarkusUnitTest() //
                .setFlatClassPath(true) // allow access to package-private members in impl package
                .withApplicationRoot(archive -> archive //
                        .addClasses(AbstractIT.class) //
                        .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml") //
                ).overrideConfigKey("quarkus.log.category.\"io.quarkus.arc\".level", "DEBUG");
    }

    @Override
    protected void setSelected(int selected) {
        clearCache();
        super.setSelected(selected);
    }

}
