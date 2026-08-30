package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class FileSkillRepository {
    private final SkillPathResolver paths;
    private final long maxFileBytes;
    private final SkillDocumentParser parser;

    @Autowired
    public FileSkillRepository(WorkflowAgentProperties properties, SkillDocumentParser parser) {
        this(properties.getSkillRoot(), properties.getMaxSkillFileBytes(), parser);
    }

    public FileSkillRepository(Path root, long maxFileBytes, SkillDocumentParser parser) {
        this.paths = new SkillPathResolver(root);
        this.maxFileBytes = maxFileBytes;
        this.parser = parser;
    }

    public synchronized List<SkillDocument> list() {
        try {
            Files.createDirectories(paths.root());
            ensureSafeRoot();
            try (Stream<Path> directories = Files.list(paths.root())) {
                return directories.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .map(path -> path.getFileName().toString())
                    .filter(code -> !code.startsWith("."))
                    .sorted(Comparator.naturalOrder())
                    .map(this::get)
                    .toList();
            }
        } catch (IOException exception) {
            throw storage("无法读取 Skill 目录。", exception);
        }
    }

    public synchronized SkillDocument get(String code) {
        Path file = paths.skillFile(code);
        ensureSafeExisting(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(ErrorCode.WORKFLOW_SKILL_NOT_FOUND, "Skill 不存在。");
        }
        try {
            long size = Files.size(file);
            if (size > maxFileBytes) {
                throw invalidSize();
            }
            return parser.parse(code, Files.readString(file, StandardCharsets.UTF_8));
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storage("无法读取 Skill 文件。", exception);
        }
    }

    public synchronized boolean exists(String code) {
        Path file = paths.skillFile(code);
        ensureSafeExisting(file);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }

    public synchronized SkillDocument create(String code, String content) {
        validateSize(content);
        SkillDocument document = parser.parse(code, content);
        Path file = paths.skillFile(code);
        Path directory = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(paths.root());
            ensureSafeRoot();
            try (FileChannel channel = lockChannel(); FileLock ignored = channel.lock()) {
                Files.createDirectory(directory);
                ensureSafeExisting(directory.resolve("SKILL.md"));
                temporary = Files.createTempFile(directory, ".skill-", ".tmp");
                Files.writeString(temporary, document.content(), StandardCharsets.UTF_8);
                moveAtomically(temporary, file, false);
                return document;
            }
        } catch (FileAlreadyExistsException exception) {
            cleanup(temporary);
            throw new BusinessException(ErrorCode.WORKFLOW_SKILL_CONFLICT, "Skill code 已存在。");
        } catch (BusinessException exception) {
            cleanup(temporary);
            cleanupEmpty(directory);
            throw exception;
        } catch (IOException exception) {
            cleanup(temporary);
            cleanupEmpty(directory);
            throw storage("无法创建 Skill 文件。", exception);
        }
    }

    public synchronized SkillDocument update(String code, String content, String expectedRevision) {
        SkillDocument current = get(code);
        if (expectedRevision == null || !current.revision().equals(expectedRevision)) {
            throw new BusinessException(ErrorCode.WORKFLOW_SKILL_CONFLICT,
                "Skill 已被其他人修改，请重新加载后再保存。");
        }
        validateSize(content);
        SkillDocument replacement = parser.parse(code, content);
        Path file = paths.skillFile(code);
        Path temporary = null;
        try {
            ensureSafeRoot();
            try (FileChannel channel = lockChannel(); FileLock ignored = channel.lock()) {
                SkillDocument lockedCurrent = get(code);
                if (!lockedCurrent.revision().equals(expectedRevision)) {
                    throw new BusinessException(ErrorCode.WORKFLOW_SKILL_CONFLICT,
                        "Skill 已被其他人修改，请重新加载后再保存。");
                }
                temporary = Files.createTempFile(file.getParent(), ".skill-", ".tmp");
                Files.writeString(temporary, replacement.content(), StandardCharsets.UTF_8);
                moveAtomically(temporary, file, true);
                return replacement;
            }
        } catch (IOException exception) {
            cleanup(temporary);
            throw storage("无法保存 Skill 文件。", exception);
        }
    }

    public synchronized SkillDocument copy(String sourceCode, String targetCode) {
        return create(targetCode, get(sourceCode).content());
    }

    public synchronized void delete(String code) {
        Path file = paths.skillFile(code);
        try {
            ensureSafeRoot();
            try (FileChannel channel = lockChannel(); FileLock ignored = channel.lock()) {
                ensureSafeExisting(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new BusinessException(ErrorCode.WORKFLOW_SKILL_NOT_FOUND, "Skill 不存在。");
                }
                Path tombstone = paths.root().resolve(".deleted-" + java.util.UUID.randomUUID());
                Files.move(file.getParent(), tombstone, StandardCopyOption.ATOMIC_MOVE);
                cleanup(tombstone.resolve("SKILL.md"));
                cleanup(tombstone);
            }
        } catch (IOException exception) {
            throw storage("无法删除 Skill 文件。", exception);
        }
    }

    private void moveAtomically(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException exception) {
            throw storage("当前文件系统不支持 Skill 原子写入。", exception);
        }
    }

    private FileChannel lockChannel() throws IOException {
        return FileChannel.open(paths.root().resolve(".workflow-skills.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    private void ensureSafeRoot() throws IOException {
        Path root = paths.root();
        if (Files.isSymbolicLink(root) || !root.toRealPath().equals(root)) {
            throw new IOException("Skill 根目录不得经过符号链接。");
        }
    }

    private void ensureSafeExisting(Path file) {
        Path directory = file.getParent();
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Skill 路径不得使用符号链接。");
        }
    }

    private void validateSize(String content) {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > maxFileBytes) {
            throw invalidSize();
        }
    }

    private BusinessException invalidSize() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "SKILL.md 超出允许的文件大小。");
    }

    private BusinessException storage(String message, Exception cause) {
        return new WorkflowSkillStorageException(message, cause);
    }

    private void cleanup(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void cleanupEmpty(Path directory) {
        cleanup(directory);
    }
}
