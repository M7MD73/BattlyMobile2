package net.kdt.pojavlaunch.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves user JVM options against the options supplied by a Minecraft version. */
public final class JvmArgumentMerger {
    private static final String[] VALUE_PREFIX_OPTIONS = {
            "-Xms", "-Xmx", "-Xmn", "-Xss"
    };

    private JvmArgumentMerger() {
    }

    public static List<String> removeOverriddenJvmOptions(
            List<String> launchArguments,
            List<String> customArguments) {
        Set<String> customOptionKeys = optionKeys(customArguments);
        if (customOptionKeys.isEmpty()) {
            return new ArrayList<>(launchArguments);
        }

        List<String> mergedArguments = new ArrayList<>(launchArguments.size());
        boolean reachedApplicationArguments = false;
        boolean consumeJvmOptionValue = false;
        for (String argument : launchArguments) {
            if (!reachedApplicationArguments && consumeJvmOptionValue) {
                consumeJvmOptionValue = false;
                mergedArguments.add(argument);
                continue;
            }
            if (!reachedApplicationArguments && isTerminalJvmOption(argument)) {
                reachedApplicationArguments = true;
                mergedArguments.add(argument);
                continue;
            }
            if (!reachedApplicationArguments && requiresSeparateValue(argument)) {
                consumeJvmOptionValue = true;
                mergedArguments.add(argument);
                continue;
            }
            if (!reachedApplicationArguments && !argument.startsWith("-")) {
                reachedApplicationArguments = true;
            }

            String optionKey = reachedApplicationArguments ? null : optionKey(argument);
            if (optionKey == null || !customOptionKeys.contains(optionKey)) {
                mergedArguments.add(argument);
            }
        }
        return mergedArguments;
    }

    public static boolean containsSameOption(List<String> arguments, String candidate) {
        String candidateKey = optionKey(candidate);
        if (candidateKey == null) {
            return false;
        }
        for (String argument : arguments) {
            if (candidateKey.equals(optionKey(argument))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> optionKeys(List<String> arguments) {
        Set<String> keys = new HashSet<>();
        if (arguments == null) {
            return keys;
        }
        for (String argument : arguments) {
            String key = optionKey(argument);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String optionKey(String argument) {
        if (argument == null) {
            return null;
        }
        String option = argument.trim();
        if (option.isEmpty() || !option.startsWith("-")) {
            return null;
        }

        if (option.startsWith("-XX:+") || option.startsWith("-XX:-")) {
            return "-XX:" + valueBeforeEquals(option, 5);
        }
        if (option.startsWith("-XX:")) {
            return "-XX:" + valueBeforeEquals(option, 4);
        }
        for (String prefix : VALUE_PREFIX_OPTIONS) {
            if (option.startsWith(prefix)) {
                return prefix;
            }
        }
        if (option.startsWith("--add-opens=") || option.startsWith("--add-exports=")) {
            int targetSeparator = option.indexOf('=', option.indexOf('=') + 1);
            return targetSeparator < 0 ? option : option.substring(0, targetSeparator);
        }
        if (option.startsWith("-javaagent:")
                || option.startsWith("-agentlib:")
                || option.startsWith("-agentpath:")) {
            int valueSeparator = option.indexOf('=');
            return valueSeparator < 0 ? option : option.substring(0, valueSeparator);
        }

        int valueSeparator = option.indexOf('=');
        return valueSeparator < 0 ? option : option.substring(0, valueSeparator);
    }

    private static String valueBeforeEquals(String option, int valueStart) {
        int valueEnd = option.indexOf('=', valueStart);
        return option.substring(valueStart, valueEnd < 0 ? option.length() : valueEnd);
    }

    private static boolean requiresSeparateValue(String argument) {
        return "-cp".equals(argument)
                || "-classpath".equals(argument)
                || "--class-path".equals(argument)
                || "-p".equals(argument)
                || "--module-path".equals(argument)
                || "--upgrade-module-path".equals(argument)
                || "--patch-module".equals(argument)
                || "--add-modules".equals(argument)
                || "--limit-modules".equals(argument);
    }

    private static boolean isTerminalJvmOption(String argument) {
        return "-jar".equals(argument)
                || "-m".equals(argument)
                || "--module".equals(argument);
    }
}
