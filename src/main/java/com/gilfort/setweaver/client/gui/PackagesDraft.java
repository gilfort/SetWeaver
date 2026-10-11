package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.AttributePackageManager.AttributePackage;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Local, unsaved edit of the attribute packages. All packages are saved together.
 */
public final class PackagesDraft {

    /** Field key of the name. */
    public static final String NAME = "name";
    /** Field key of the priority. */
    public static final String PRIORITY = "priority";
    /** Field key of the enabled flag. */
    public static final String ENABLED = "enabled";
    /** Field key of the attribute list. */
    public static final String ATTRIBUTES = "attributes";
    private static final Gson GSON = new Gson();

    private final List<AttributePackage> original = new ArrayList<>();
    private final List<AttributePackage> packages = new ArrayList<>();
    private boolean saving;

    /**
     * Starts editing.
     *
     * @param stored the stored packages
     */
    public PackagesDraft(List<AttributePackage> stored) {
        stored.forEach(p -> {
            this.original.add(p.copy());
            this.packages.add(p.copy());
        });
    }

    /** @return the edited packages (mutable) */
    public List<AttributePackage> packages() {
        return this.packages;
    }

    /** @return whether anything differs from the stored packages */
    public boolean dirty() {
        return !GSON.toJson(this.original).equals(GSON.toJson(this.packages));
    }

    /**
     * @param index index of a package
     * @return its changed field keys; a new package has all keys
     */
    public Set<String> changed(int index) {
        Set<String> changed = new LinkedHashSet<>();
        if (index < 0 || index >= this.packages.size()) {
            return changed;
        }
        AttributePackage now = this.packages.get(index);
        AttributePackage before = index < this.original.size() ? this.original.get(index) : null;
        if (before == null || !Objects.equals(before.getName(), now.getName())) {
            changed.add(NAME);
        }
        if (before == null || before.getPriority() != now.getPriority()) {
            changed.add(PRIORITY);
        }
        if (before == null || before.isEnabled() != now.isEnabled()) {
            changed.add(ENABLED);
        }
        if (before == null || !before.getAttributes().equals(now.getAttributes())) {
            changed.add(ATTRIBUTES);
        }
        return changed;
    }

    /** @return whether every package has a name */
    public boolean valid() {
        return this.packages.stream().allMatch(p -> p.getName() != null && !p.getName().isBlank());
    }

    /** @return the packages as JSON for the save packet */
    public String toJson() {
        return GSON.toJson(this.packages);
    }

    /** @return whether a save is on its way */
    public boolean saving() {
        return this.saving;
    }

    /** @param saving whether a save is on its way */
    public void setSaving(boolean saving) {
        this.saving = saving;
    }
}
