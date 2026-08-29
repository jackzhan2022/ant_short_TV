package com.antshorttv.script;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ScriptEpisodeReconciler {
    private static final Pattern HEADING_PREFIX = Pattern.compile(
        "^(?:第\\s*(?:\\d{1,3}|[零〇一二两三四五六七八九十百]+)\\s*集|EP\\s*0*\\d{1,3})\\s*[:：\\-]?\\s*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NON_SEMANTIC = Pattern.compile("[\\p{P}\\p{S}\\s]+");

    public EpisodeReconciliation reconcile(
        List<ExistingEpisode> existingEpisodes,
        List<ScriptEpisodeResponse> incomingEpisodes
    ) {
        List<ExistingEpisode> existing = existingEpisodes == null ? List.of() : existingEpisodes;
        List<ScriptEpisodeResponse> incoming = incomingEpisodes == null ? List.of() : incomingEpisodes;
        Map<String, List<ExistingEpisode>> byHeading = indexByHeading(existing);
        Map<String, List<ExistingEpisode>> byFingerprint = indexByFingerprint(existing);
        Map<Integer, List<ExistingEpisode>> byEpisodeNo = indexByEpisodeNo(existing);
        boolean stableEpisodeNumbers = existing.size() == incoming.size()
            && uniqueEpisodeNumbers(existing.stream().map(ExistingEpisode::episodeNo).toList())
            && uniqueEpisodeNumbers(incoming.stream().map(ScriptEpisodeResponse::episodeNo).toList());
        Set<Long> matchedIds = new HashSet<>();
        List<ReconciledEpisode> active = new ArrayList<>();

        for (ScriptEpisodeResponse episode : incoming) {
            String headingKey = headingKey(episode.title());
            String fingerprint = fingerprint(episode.content());
            Match match = uniqueUnmatched(byHeading.get(headingKey), matchedIds);
            String status = "HEADING_MATCHED";
            boolean ambiguous = match.ambiguous();
            if (match.episode() == null && !ambiguous) {
                match = uniqueUnmatched(byFingerprint.get(fingerprint), matchedIds);
                status = "CONTENT_MATCHED";
                ambiguous = match.ambiguous();
            }
            if (match.episode() == null && !ambiguous && stableEpisodeNumbers
                && (headingKey == null || hasGenericHeading(byEpisodeNo.get(episode.episodeNo())))) {
                match = uniqueUnmatched(byEpisodeNo.get(episode.episodeNo()), matchedIds);
                status = "POSITION_MATCHED";
                ambiguous = match.ambiguous();
            }

            if (match.episode() != null) {
                matchedIds.add(match.episode().id());
                active.add(new ReconciledEpisode(
                    match.episode().id(),
                    match.episode().stableKey(),
                    episode.episodeNo(),
                    episode.title(),
                    episode.content(),
                    episode.summary(),
                    fingerprint,
                    headingKey,
                    status
                ));
            } else {
                active.add(new ReconciledEpisode(
                    null,
                    newStableKey(episode, fingerprint),
                    episode.episodeNo(),
                    episode.title(),
                    episode.content(),
                    episode.summary(),
                    fingerprint,
                    headingKey,
                    ambiguous ? "AMBIGUOUS" : "CREATED"
                ));
            }
        }

        List<String> retired = existing.stream()
            .filter(item -> !matchedIds.contains(item.id()))
            .map(ExistingEpisode::stableKey)
            .toList();
        return new EpisodeReconciliation(List.copyOf(active), retired);
    }

    public String fingerprint(String content) {
        String normalized = content == null
            ? ""
            : NON_SEMANTIC.matcher(content.toLowerCase(Locale.ROOT)).replaceAll("");
        return sha256(normalized);
    }

    private Map<String, List<ExistingEpisode>> indexByHeading(List<ExistingEpisode> existing) {
        Map<String, List<ExistingEpisode>> result = new LinkedHashMap<>();
        existing.forEach(item -> {
            String key = headingKey(item.title());
            if (key != null) {
                result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
            }
        });
        return result;
    }

    private Map<String, List<ExistingEpisode>> indexByFingerprint(List<ExistingEpisode> existing) {
        Map<String, List<ExistingEpisode>> result = new LinkedHashMap<>();
        existing.forEach(item -> result
            .computeIfAbsent(fingerprint(item.content()), ignored -> new ArrayList<>())
            .add(item));
        return result;
    }

    private Map<Integer, List<ExistingEpisode>> indexByEpisodeNo(List<ExistingEpisode> existing) {
        Map<Integer, List<ExistingEpisode>> result = new LinkedHashMap<>();
        existing.stream().filter(item -> item.episodeNo() != null).forEach(item -> result
            .computeIfAbsent(item.episodeNo(), ignored -> new ArrayList<>()).add(item));
        return result;
    }

    private boolean uniqueEpisodeNumbers(List<Integer> numbers) {
        return numbers.stream().allMatch(number -> number != null && number > 0)
            && new HashSet<>(numbers).size() == numbers.size();
    }

    private boolean hasGenericHeading(List<ExistingEpisode> candidates) {
        return candidates != null && !candidates.isEmpty()
            && candidates.stream().anyMatch(item -> headingKey(item.title()) == null);
    }

    private Match uniqueUnmatched(List<ExistingEpisode> candidates, Set<Long> matchedIds) {
        if (candidates == null) {
            return Match.none();
        }
        List<ExistingEpisode> unmatched = candidates.stream()
            .filter(item -> !matchedIds.contains(item.id()))
            .toList();
        if (unmatched.size() == 1) {
            return new Match(unmatched.get(0), false);
        }
        return new Match(null, unmatched.size() > 1);
    }

    private String headingKey(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String semanticTitle = HEADING_PREFIX.matcher(title.trim()).replaceFirst("");
        semanticTitle = NON_SEMANTIC.matcher(semanticTitle.toLowerCase(Locale.ROOT)).replaceAll("");
        return semanticTitle.isBlank() ? null : semanticTitle;
    }

    private String newStableKey(ScriptEpisodeResponse episode, String fingerprint) {
        String source = (episode.episodeNo() == null ? 0 : episode.episodeNo())
            + ":" + (headingKey(episode.title()) == null ? "" : headingKey(episode.title()))
            + ":" + fingerprint;
        return "episode-" + sha256(source).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ExistingEpisode(Long id, String stableKey, Integer episodeNo, String title, String content) {}

    public record ReconciledEpisode(
        Long id,
        String stableKey,
        Integer episodeNo,
        String title,
        String content,
        String summary,
        String contentFingerprint,
        String headingKey,
        String status
    ) {}

    public record EpisodeReconciliation(List<ReconciledEpisode> active, List<String> retiredStableKeys) {}

    private record Match(ExistingEpisode episode, boolean ambiguous) {
        private static Match none() {
            return new Match(null, false);
        }
    }
}
