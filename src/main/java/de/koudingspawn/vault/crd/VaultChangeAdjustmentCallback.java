package de.koudingspawn.vault.crd;

public class VaultChangeAdjustmentCallback {

    private String type;
    private String name;

    public VaultChangeAdjustmentCallback() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return type + "/" + name;
    }
}
