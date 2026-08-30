package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class SkillPathResolver {
    private static final Pattern SAFE_CODE = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    private final Path root;

    public SkillPathResolver(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Skill root is required");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path skillFile(String code) {
        if (code == null || code.length() > 128 || !SAFE_CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Skill code 只能使用小写 kebab-case 格式。");
        }
        Path file = root.resolve(code).resolve("SKILL.md").normalize();
        if (!file.startsWith(root) || !file.getParent().getParent().equals(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Skill code 路径不安全。");
        }
        return file;
    }
}
