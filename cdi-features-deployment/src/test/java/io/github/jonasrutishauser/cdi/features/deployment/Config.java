package io.github.jonasrutishauser.cdi.features.deployment;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class Config {
    private int selected;
    private boolean destroyed;
    private boolean selectorDestroyed;

    public void setSelected(int selected) {
        this.selected = selected;
        destroyed = selectorDestroyed = false;
    }

    public int getSelected() {
        return selected;
    }

    public boolean isDestroyed() {
        return destroyed && selectorDestroyed;
    }

    public void setDestroyed() {
        this.destroyed = true;
    }

    public void setSelectorDestroyed() {
        this.selectorDestroyed = true;
    }
}