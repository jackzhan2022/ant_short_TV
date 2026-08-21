package com.antshorttv.script;

import java.util.List;

record ScriptElementExtractionResult(
    ScriptElementType elementType,
    List<CharacterElement> characters,
    List<SceneElement> scenes,
    List<PropElement> props
) {
    ScriptElementExtractionResult {
        characters = characters == null ? List.of() : List.copyOf(characters);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
        props = props == null ? List.of() : List.copyOf(props);
    }

    record CharacterElement(
        String name,
        String roleType,
        String gender,
        String ageRange,
        String identity,
        List<String> personality,
        String appearance,
        String prompt
    ) {
        CharacterElement {
            personality = personality == null ? List.of() : List.copyOf(personality);
        }
    }

    record SceneElement(
        String name,
        String sceneType,
        String atmosphere,
        String description,
        String visualStyle,
        String prompt
    ) {
    }

    record PropElement(
        String name,
        String propType,
        String appearance,
        String plotFunction,
        String relatedCharacter,
        String prompt
    ) {
    }
}
