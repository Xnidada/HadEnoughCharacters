package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.Transformer;
import mezz.jei.config.Constants;
import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Iterator;

/**
 * Author: Towdium
 * Date:   13/06/17
 */

public class TransformerHei extends Transformer.Default {

    private static final String PREFIX_INFO = "mezz.jei.search.PrefixInfo";
    private static final String DEFAULT_SEARCH_FACTORY = "mezz.jei.startup.JeiStarter$1";

    @Override
    public boolean accepts(String name) {
        boolean loading = PREFIX_INFO.equals(name) || DEFAULT_SEARCH_FACTORY.equals(name);
        if (loading) {
            boolean isHei = Loader.instance().getModList()
                    .stream()
                    .anyMatch(mod -> mod.getModId().equals(Constants.MOD_ID) && mod.getName().equals(Constants.NAME));
            if (!isHei) return false;
        }
        if (!HechConfig.enableHEI || !loading) return false;
        return PREFIX_INFO.equals(name) || hasAdvancedSearchApi();
    }

    @Override
    public void transform(ClassNode n) {
        HechCore.LOG.info("Transforming class " + n.name + " for HEI integration.");
        if ("mezz/jei/startup/JeiStarter$1".equals(n.name)) {
            transformDefaultSearchFactory(n);
            return;
        }
        Transformer.findMethod(n, "<clinit>").ifPresent(methodNode ->
                transformInvokeLambda(methodNode,
                        "mezz/jei/search/GeneralizedSuffixTree",
                        "<init>",
                        "()V",
                        "me/towdium/hecharacters/util/Match$FakeTree",
                        "<init>",
                        "()V"
                ));
        if (JechConfig.enableForceQuote) Transformer.findMethod(n, "parseSearchToken").ifPresent(methodNode -> {
            InsnList list = methodNode.instructions;

            for (int i = 0; i < list.size(); i++) {
                AbstractInsnNode node = list.get(i);
                if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode methodInsn = (MethodInsnNode) node;
                    if ("java/util/regex/Pattern".equals(methodInsn.owner) && "matcher".equals(methodInsn.name) && "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;".equals(methodInsn.desc)) {
                        list.insert(node.getPrevious(), new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "me/towdium/hecharacters/util/Match", "wrap",
                                "(Ljava/lang/String;)Ljava/lang/String;", false));
                    }
                }
            }
        });
    }

    private static boolean hasAdvancedSearchApi() {
        try {
            Class.forName("mezz.jei.api.search.ISearchIndex", false, TransformerHei.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void transformDefaultSearchFactory(ClassNode n) {
        Transformer.findMethod(n, "create").ifPresent(methodNode -> {
            methodNode.instructions.clear();
            methodNode.instructions.add(new TypeInsnNode(Opcodes.NEW, "mezz/jei/search/SearchIndexBuilder"));
            methodNode.instructions.add(new InsnNode(Opcodes.DUP));
            methodNode.instructions.add(new TypeInsnNode(Opcodes.NEW, "me/towdium/hecharacters/util/Match$FakeTree"));
            methodNode.instructions.add(new InsnNode(Opcodes.DUP));
            methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                    "me/towdium/hecharacters/util/Match$FakeTree", "<init>", "()V", false));
            methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                    "mezz/jei/search/SearchIndexBuilder", "<init>",
                    "(Lmezz/jei/api/search/ISearchIndex;)V", false));
            methodNode.instructions.add(new InsnNode(Opcodes.ARETURN));
            methodNode.maxStack = 4;
            methodNode.maxLocals = 1;
        });
    }

    private static boolean transformInvokeLambda(
            MethodNode method, String owner, String name, String desc,
            String newOwner, String newName, String newDesc
    ) {
        boolean ret = false;
        Iterator<AbstractInsnNode> i = method.instructions.iterator();
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
            int op = node.getOpcode();
            if (node instanceof InvokeDynamicInsnNode && op == Opcodes.INVOKEDYNAMIC) {
                InvokeDynamicInsnNode insnNode = ((InvokeDynamicInsnNode) node);
                if (insnNode.bsmArgs[1] instanceof Handle) {
                    Handle h = ((Handle) insnNode.bsmArgs[1]);
                    if (!(!h.getOwner().equals(owner) || !h.getName().equals(name) || !h.getDesc().equals(desc))) {
                        insnNode.bsmArgs[1] = new Handle(h.getTag(), newOwner, newName, newDesc, h.isInterface());
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }
}
