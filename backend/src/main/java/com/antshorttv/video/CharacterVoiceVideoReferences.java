package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.material.ProjectAudioService;
import com.antshorttv.material.StoryboardCharacterVoiceService;
import com.antshorttv.script.StoryboardPromptCompiler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class CharacterVoiceVideoReferences {
    private final StoryboardCharacterVoiceService voices;
    private final ProjectAudioService audio;
    private final ModelAccessibleVideoUrlResolver urls;

    public CharacterVoiceVideoReferences(StoryboardCharacterVoiceService voices, ProjectAudioService audio,
        ModelAccessibleVideoUrlResolver urls) {
        this.voices = voices; this.audio = audio; this.urls = urls;
    }

    public boolean hasVoices(Long tenant, Long project, Long storyboard) {
        return !voices.choicesForVideo(tenant, project, storyboard).isEmpty();
    }

    public Augmentation append(Long tenant, Long project, Long storyboard,
        List<VideoTaskReferenceResolver.ResolvedReference> existing) {
        var references = new ArrayList<>(existing);
        var assignments = new ArrayList<VoiceAssignment>();
        var byAudio = new LinkedHashMap<String, StoryboardPromptCompiler.Reference>();
        var assignedShapes = new LinkedHashSet<String>();
        int audioIndex = existing.stream().filter(ref -> "AUDIO".equals(ref.reference().mediaType()))
            .mapToInt(ref -> ref.reference().mediaIndex()).max().orElse(0);
        int sortOrder = existing.stream().mapToInt(ref -> ref.reference().sortOrder()).max().orElse(-1) + 1;
        for (var choice : voices.choicesForVideo(tenant, project, storyboard)) {
            if (!assignedShapes.add(choice.characterId() + ":" + choice.variantId())) continue;
            String identity = choice.sourceProjectId() + ":" + choice.resourceType() + ":" + choice.resourceId();
            var reference = byAudio.get(identity);
            if (reference == null) {
                var media = audio.trustedMedia(tenant, choice.sourceProjectId(), choice.resourceType(), choice.resourceId());
                int index = ++audioIndex;
                reference = new StoryboardPromptCompiler.Reference("AUDIO", index, "音频" + index, "reference_audio",
                    choice.resourceType(), choice.resourceId(), null, "CHARACTER", choice.characterId(),
                    choice.characterName() + "音色", sortOrder++, choice.referenceRole(), true);
                references.add(new VideoTaskReferenceResolver.ResolvedReference(reference, reference.displayName(),
                    media.storagePath(), urls.resolve(media.storagePath()), media.format(), media.fileSize(),
                    null, null, media.durationSeconds(), null, media.previewUrl()));
                byAudio.put(identity, reference);
            }
            assignments.add(new VoiceAssignment(choice.characterId(), choice.characterName(), choice.variantId(),
                choice.variantName(), choice.sourceProjectId(), choice.resourceType(), choice.resourceId(),
                reference.compiledLabel(), choice.overridden()));
        }
        return new Augmentation(List.copyOf(references), List.copyOf(assignments));
    }

    public record VoiceAssignment(Long characterId, String characterName, Long variantId, String variantName,
        Long sourceProjectId, String resourceType, Long resourceId, String audioLabel, boolean overridden) {}

    public record Augmentation(List<VideoTaskReferenceResolver.ResolvedReference> references, List<VoiceAssignment> assignments) {
        public List<VideoTaskReferenceResolver.ResolvedReference> selectedReferences(List<VideoTaskReferenceResolver.ResolvedReference> kept) {
            if (assignments.isEmpty()) return kept;
            // Audio labels follow attachment order. Never silently drop an audio and leave its label pointing at another voice.
            for (var audio : references) {
                if ("AUDIO".equals(audio.reference().mediaType()) && kept.stream().noneMatch(ref ->
                    ref.reference().sourceType().equals(audio.reference().sourceType())
                        && ref.reference().sourceId().equals(audio.reference().sourceId()))) throw limitExceeded();
            }
            var orderedAudio = kept.stream().filter(ref -> "AUDIO".equals(ref.reference().mediaType()))
                .sorted(java.util.Comparator.comparingInt(ref -> ref.reference().mediaIndex())).iterator();
            return kept.stream().map(ref -> "AUDIO".equals(ref.reference().mediaType()) ? orderedAudio.next() : ref).toList();
        }

        public String prompt(String original, List<VideoTaskReferenceResolver.ResolvedReference> kept) {
            StringBuilder text = new StringBuilder(original);
            for (var voice : assignments) {
                boolean included = kept.stream().anyMatch(ref -> "AUDIO".equals(ref.reference().mediaType())
                    && ref.reference().sourceType().equals(voice.resourceType()) && ref.reference().sourceId().equals(voice.resourceId()));
                if (!included) throw limitExceeded();
                String image = null;
                int imageIndex = 0;
                for (var item : kept) {
                    var ref = item.reference();
                    if (!"IMAGE".equals(ref.mediaType())) continue;
                    imageIndex++;
                    if ("CHARACTER".equals(ref.assetType()) && Objects.equals(ref.assetId(), voice.characterId())
                        && Objects.equals(ref.variantId(), voice.variantId())) { image = "图片" + imageIndex; break; }
                }
                text.append("\n角色音色：角色「").append(voice.characterName()).append("」");
                if (voice.variantName() != null && !voice.variantName().isBlank()) text.append("（形态「").append(voice.variantName()).append("」）");
                if (image != null) text.append("对应").append(image).append("，");
                text.append("使用").append(voice.audioLabel()).append("作为音色参考，保持该角色发声与参考音色一致。");
            }
            return text.toString();
        }

        private BusinessException limitExceeded() {
            return new BusinessException(ErrorCode.VALIDATION_ERROR,
                "角色音色及参考音频超过所选模型的音频数量或总时长限制，请调整分镜音色或更换模型。");
        }
    }
}
