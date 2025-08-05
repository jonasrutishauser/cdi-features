package io.github.jonasrutishauser.cdi.features.deployment;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class Config {
    private int selected;
    private boolean destroyed;
    private boolean selectorDestroyed;
    private boolean feature3Created;

    public void setSelected(int selected) {
        this.selected = selected;
        destroyed = selectorDestroyed = feature3Created = false;
    }

    public int getSelected() {
        return selected;
    }

    public boolean isDestroyed() {
        return destroyed && selectorDestroyed;
    }

    public boolean isFeature3Created() {
        return feature3Created;
    }

    public void setDestroyed() {
        this.destroyed = true;
    }

    public void setSelectorDestroyed() {
        this.selectorDestroyed = true;
    }

    public void setFeature3Created() {
        this.feature3Created = true;
    }
}