import java.nio.file.*;
import java.util.*;
import org.springframework.asm.*;

/** Applies only supplier-code compatibility to the exact currently deployed class files. */
public class PatchArkSupplier {
    static final String HELPER = "com/antshorttv/ai/VolcengineProviderCodes";
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (String name : List.of("ai/AiModelRouter", "video/AiVideoTaskService",
                "freecreation/FreeCreationService", "freecreation/FreeCreationExecutionHandler", "video/VideoReferenceDiagnostics")) {
            Path path = root.resolve("com/antshorttv/" + name + ".class");
            if (!Files.exists(path)) {
                if (name.equals("video/VideoReferenceDiagnostics")) continue;
                throw new IllegalStateException("Missing deployed class: " + name);
            }
            ClassReader reader = new ClassReader(Files.readAllBytes(path));
            ClassWriter writer = new ClassWriter(reader, 0);
            int[] edits = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                        @Override public void visitMethodInsn(int opcode, String owner, String member, String desc, boolean itf) {
                            if (!name.equals("ai/AiModelRouter") && opcode == Opcodes.INVOKEVIRTUAL
                                    && owner.equals("java/lang/String") && member.equals("equalsIgnoreCase")
                                    && desc.equals("(Ljava/lang/String;)Z")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "matches", "(Ljava/lang/String;Ljava/lang/String;)Z", false);
                                edits[0]++;
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, member, desc, itf);
                            if (name.equals("ai/AiModelRouter") && method.equals("route")
                                    && owner.equals("com/antshorttv/ai/AiProviderEntity") && member.equals("getCode")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "adapterCode", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                edits[0]++;
                            }
                        }
                    };
                }
            }, 0);
            if (edits[0] == 0 || (name.equals("ai/AiModelRouter") && edits[0] != 1))
                throw new IllegalStateException("Unexpected patch count for " + name + ": " + edits[0]);
            Files.write(path, writer.toByteArray());
            System.out.println(name + " patched_calls=" + edits[0]);
        }
    }
}
