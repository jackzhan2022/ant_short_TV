package com.antshorttv.script;

/** Validation follows trimming in AssetNameService so all callers use the same length rule. */
record RenameAssetRequest(String name) {}
