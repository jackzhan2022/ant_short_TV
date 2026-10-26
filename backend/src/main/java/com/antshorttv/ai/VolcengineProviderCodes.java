package com.antshorttv.ai;

/** Supplier identities that use the existing Volcengine Ark protocol. */
public final class VolcengineProviderCodes {
    public static final String ARK = "VOLCENGINE_ARK";
    public static final String XINJI = "VOLCENGINE_ARK_XINJI";

    private VolcengineProviderCodes() {}

    public static boolean isArk(String code) {
        return ARK.equalsIgnoreCase(code) || XINJI.equalsIgnoreCase(code);
    }

    public static String adapterCode(String code) {
        return isArk(code) ? ARK : code;
    }

    /** Preserves existing comparisons when updating an already packaged Ark integration. */
    public static boolean matches(String expected, String actual) {
        return ARK.equalsIgnoreCase(expected) ? isArk(actual) : expected.equalsIgnoreCase(actual);
    }
}
