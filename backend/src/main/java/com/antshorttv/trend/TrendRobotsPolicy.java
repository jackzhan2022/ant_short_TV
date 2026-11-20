package com.antshorttv.trend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class TrendRobotsPolicy {
    private record Rule(boolean allow, String path) {}
    private record Group(List<String> agents, List<Rule> rules) {}
    private TrendRobotsPolicy() {}

    static boolean allows(String body, String path) {
        return allows(body,path,"antshorttvtrendcollector");
    }

    static boolean allowsForCollection(String body, String path) {
        return java.util.stream.Stream.of("antshorttvtrendcollector", "chatgpt-user", "gptbot", "openai")
            .allMatch(agent -> allows(body,path,agent));
    }

    private static boolean allows(String body, String path, String agent) {
        List<Group> groups = new ArrayList<>();
        Group group = new Group(new ArrayList<>(), new ArrayList<>());
        boolean hasRules = false;
        for (String line : body.split("\\R")) {
            String[] parts = line.split("#", 2)[0].trim().split(":", 2);
            if (parts.length != 2) continue;
            String key = parts[0].trim().toLowerCase(Locale.ROOT), value = parts[1].trim();
            if (key.equals("user-agent")) {
                if (hasRules) { groups.add(group); group = new Group(new ArrayList<>(), new ArrayList<>()); hasRules = false; }
                group.agents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow") || key.equals("disallow")) {
                hasRules = true;
                if (!value.isBlank()) group.rules.add(new Rule(key.equals("allow"), value));
            }
        }
        groups.add(group);
        boolean specific = groups.stream().anyMatch(g -> g.agents.stream()
            .anyMatch(a -> !a.equals("*") && agent.contains(a)));
        int best = -1;
        boolean allowed = true;
        for (Group g : groups) {
            boolean applies = specific ? g.agents.stream().anyMatch(a -> !a.equals("*") && agent.contains(a)) : g.agents.contains("*");
            if (!applies) continue;
            for (Rule rule : g.rules) {
                String p = rule.path;
                boolean end = p.endsWith("$");
                if (end) p = p.substring(0, p.length() - 1);
                String regex = "^" + String.join(".*", java.util.Arrays.stream(p.split("\\*", -1)).map(Pattern::quote).toList()) + (end ? "$" : "");
                int length = p.replace("*", "").length();
                if (Pattern.compile(regex).matcher(path).find() && (length > best || (length == best && rule.allow))) {
                    best = length; allowed = rule.allow;
                }
            }
        }
        return allowed;
    }
}
