package com.gilfort.setweaver.seteffects;

import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Layout of the set definition folder {@code config/setweaver/set_armor/} and validation of paths sent by clients.
 *
 * <pre>
 *   {role}/{level}/{namespace}__{tagpath}.json        one role and level
 *   all_roles/{level}/{namespace}__{tagpath}.json     all roles, one level
 *   all_roles_all_levels/{namespace}__{tagpath}.json  everyone
 * </pre>
 */
public final class SetFiles {

    /** Folder name for sets that apply to all roles of one level. */
    public static final String ALL_ROLES_DIR = "all_roles";
    /** Folder name for sets that apply to everyone. */
    public static final String ALL_ROLES_ALL_LEVELS_DIR = "all_roles_all_levels";
    /** Separator between namespace and path in file names. */
    public static final String TAG_SEPARATOR = "__";
    /** File extension of set definitions. */
    public static final String EXTENSION = ".json";

    private static final Pattern ROLE_PATTERN = Pattern.compile("[a-z0-9_.+-]+");

    private SetFiles() {
    }

    /** @return the absolute set definition folder */
    public static Path baseDir() {
        return FMLPaths.CONFIGDIR.get().resolve("setweaver").resolve("set_armor");
    }

    /**
     * @param role a role name
     * @return whether the name can be used as a folder name and command argument
     */
    public static boolean isValidRole(String role) {
        return role != null && ROLE_PATTERN.matcher(role).matches()
                && !role.equals(ALL_ROLES_DIR) && !role.equals(ALL_ROLES_ALL_LEVELS_DIR) && !role.startsWith(".");
    }

    /**
     * Builds the relative path of a set file.
     *
     * @param role  the role or {@link ArmorSetDataRegistry#WILDCARD_ROLE}
     * @param level the level or {@link ArmorSetDataRegistry#WILDCARD_LEVEL}
     * @param tag   the item tag id
     * @return the path relative to {@link #baseDir()}, with {@code /} separators
     */
    public static String relativePath(String role, int level, String tag) {
        String fileName = tag.replace(":", TAG_SEPARATOR).replace("/", TAG_SEPARATOR) + EXTENSION;
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(role);
        if (wildRole && level == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            return ALL_ROLES_ALL_LEVELS_DIR + "/" + fileName;
        }
        if (wildRole) {
            return ALL_ROLES_DIR + "/" + level + "/" + fileName;
        }
        return role + "/" + level + "/" + fileName;
    }

    /**
     * Turns a file name into the item tag it stands for. The first {@code __} separates namespace and path, every
     * further {@code __} stands for a {@code /} in the path.
     *
     * @param fileName e.g. {@code c__armors__iron.json} for {@code c:armors/iron}
     * @return the tag id, or {@code null} if the name is not valid
     */
    public static Identifier tagFromFileName(String fileName) {
        if (!fileName.endsWith(EXTENSION)) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - EXTENSION.length());
        int split = base.indexOf(TAG_SEPARATOR);
        if (split <= 0) {
            return null;
        }
        String path = base.substring(split + TAG_SEPARATOR.length()).replace(TAG_SEPARATOR, "/");
        return Identifier.tryBuild(base.substring(0, split), path);
    }

    /**
     * Resolves a relative path received from a client. Only paths that follow the folder layout are accepted, so a
     * client can never read or write outside the set folder.
     *
     * @param relativePath the path with {@code /} separators
     * @return the absolute file, or empty if the path is not a valid set file location
     */
    public static Optional<Path> resolveClientPath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty() || relativePath.contains("\\")) {
            return Optional.empty();
        }
        String[] segments = relativePath.split("/", -1);
        String fileName = segments[segments.length - 1];
        if (tagFromFileName(fileName) == null) {
            return Optional.empty();
        }
        if (segments.length == 2) {
            if (!segments[0].equals(ALL_ROLES_ALL_LEVELS_DIR)) {
                return Optional.empty();
            }
        } else if (segments.length == 3) {
            if (!segments[0].equals(ALL_ROLES_DIR) && !isValidRole(segments[0])) {
                return Optional.empty();
            }
            if (!isPositiveInt(segments[1])) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        Path base = baseDir().toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        return target.startsWith(base) ? Optional.of(target) : Optional.empty();
    }

    private static boolean isPositiveInt(String s) {
        try {
            return Integer.parseInt(s) >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
