import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Generates compile-only Java stubs for every external (non-JDK, non-mod) class member
 * referenced from the given jar's bytecode. Hierarchy and generics come from hints.txt.
 */
public class StubGen {
    static final String MOD = "com/skyblockminer/";
    static final class Stub {
        String name; boolean itf; boolean annotation; boolean enumLike;
        final Map<String, String[]> fields = new TreeMap<>();   // name -> {desc, static}
        final Set<String> methods = new TreeSet<>();            // static|name|desc
        String sam;                                             // name|desc
    }
    static final Map<String, Stub> stubs = new TreeMap<>();

    static boolean external(String internal) {
        return internal != null && !internal.startsWith("java/") && !internal.startsWith("javax/") && !internal.startsWith(MOD)
            && !internal.startsWith("com/google/gson/") && !internal.startsWith("org/slf4j/") && !internal.startsWith("org/lwjgl/")
            && !internal.startsWith("[");
    }
    static Stub stub(String internal) {
        if (internal.startsWith("[")) return null;
        return stubs.computeIfAbsent(internal, n -> { Stub s = new Stub(); s.name = n; return s; });
    }
    static void type(Type t) {
        while (t.getSort() == Type.ARRAY) t = t.getElementType();
        if (t.getSort() == Type.OBJECT && external(t.getInternalName())) stub(t.getInternalName());
        if (t.getSort() == Type.METHOD) { type(t.getReturnType()); for (Type a : t.getArgumentTypes()) type(a); }
    }
    static void desc(String d) { type(Type.getType(d)); }

    public static void main(String[] args) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(args[0]))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (!e.getName().endsWith(".class")) continue;
                new ClassReader(zip.readAllBytes()).accept(new Collector(), 0);
            }
        }
        Path out = Paths.get(args[1]);
        Map<String, List<String>> nested = new TreeMap<>();
        for (String n : stubs.keySet()) if (n.contains("$")) nested.computeIfAbsent(n.substring(0, n.indexOf('$')), k -> new ArrayList<>()).add(n);
        for (String n : new ArrayList<>(stubs.keySet())) if (n.contains("$")) stub(n.substring(0, n.indexOf('$')));
        for (Stub s : new ArrayList<>(stubs.values())) {
            if (s.name.contains("$")) continue;
            StringBuilder b = new StringBuilder();
            String pkg = s.name.substring(0, s.name.lastIndexOf('/')).replace('/', '.');
            b.append("package ").append(pkg).append(";\n\n");
            emit(b, s, nested.getOrDefault(s.name, List.of()), "", true);
            Path f = out.resolve(s.name + ".java");
            Files.createDirectories(f.getParent());
            Files.writeString(f, b.toString());
        }
        System.out.println("stubs: " + stubs.size());
    }

    static String simple(String internal) { String n = internal.substring(internal.lastIndexOf('/') + 1); return n.substring(n.lastIndexOf('$') + 1); }
    static String java(Type t) {
        switch (t.getSort()) {
            case Type.ARRAY: return java(t.getElementType()) + "[]".repeat(t.getDimensions());
            case Type.OBJECT: return t.getClassName().replace('$', '.');
            default: return t.getClassName();
        }
    }
    static String dflt(Type t) {
        switch (t.getSort()) {
            case Type.VOID: return "";
            case Type.BOOLEAN: return "false";
            case Type.OBJECT: case Type.ARRAY: return "null";
            default: return "0";
        }
    }

    static void emit(StringBuilder b, Stub s, List<String> nestedNames, String indent, boolean top) {
        String kind = s.annotation ? "@interface" : s.itf ? "interface" : "class";
        b.append(indent).append("public ").append(top ? "" : "static ").append(kind).append(" ").append(simple(s.name)).append(" {\n");
        String in = indent + "    ";
        for (Map.Entry<String, String[]> f : s.fields.entrySet()) {
            Type t = Type.getType(f.getValue()[0]);
            boolean st = f.getValue()[1].equals("1");
            b.append(in).append("public ").append(st ? "static " : "").append(java(t)).append(" ").append(f.getKey());
            if (s.itf) b.append(" = ").append(dflt(t));
            b.append(";\n");
        }
        if (s.sam != null) {
            String[] p = s.sam.split("\\|");
            Type m = Type.getMethodType(p[1]);
            b.append(in).append(java(m.getReturnType())).append(" ").append(p[0]).append(params(m)).append(";\n");
        }
        for (String m : s.methods) {
            String[] p = m.split("\\|");
            boolean st = p[0].equals("1");
            Type mt = Type.getMethodType(p[2]);
            if (s.sam != null && s.sam.equals(p[1] + "|" + p[2])) continue;
            if (s.annotation) { b.append(in).append(java(mt.getReturnType())).append(" ").append(p[1]).append("() default ").append(dflt(mt.getReturnType())).append(";\n"); continue; }
            if (p[1].equals("<init>")) { b.append(in).append("public ").append(simple(s.name)).append(params(mt)).append(" {}\n"); continue; }
            String body = mt.getReturnType().getSort() == Type.VOID ? "{}" : "{ return " + dflt(mt.getReturnType()) + "; }";
            b.append(in).append("public ").append(st ? "static " : (s.itf ? "default " : "")).append(java(mt.getReturnType())).append(" ").append(p[1]).append(params(mt)).append(" ").append(body).append("\n");
        }
        for (String n : nestedNames) emit(b, stubs.get(n), List.of(), in, false);
        b.append(indent).append("}\n");
    }
    static String params(Type m) {
        StringJoiner j = new StringJoiner(", ", "(", ")");
        Type[] a = m.getArgumentTypes();
        for (int i = 0; i < a.length; i++) j.add(java(a[i]) + " a" + i);
        return j.toString();
    }

    static final class Collector extends ClassVisitor {
        Collector() { super(Opcodes.ASM9); }
        @Override public void visit(int v, int acc, String name, String sig, String sup, String[] itfs) {
            if (external(sup)) stub(sup);
            if (itfs != null) for (String i : itfs) if (external(i)) stub(i).itf = true;
        }
        @Override public AnnotationVisitor visitAnnotation(String d, boolean vis) { return ann(d); }
        @Override public FieldVisitor visitField(int acc, String n, String d, String s, Object v) { desc(d); return null; }
        @Override public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] ex) {
            desc(d);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String ad, boolean vis) { return ann(ad); }
                @Override public void visitMethodInsn(int op, String owner, String name, String md, boolean itf) {
                    desc(md);
                    if (!external(owner)) return;
                    Stub st = stub(owner); if (st == null) return;
                    if (itf) st.itf = true;
                    st.methods.add((op == Opcodes.INVOKESTATIC ? "1" : "0") + "|" + name + "|" + md);
                }
                @Override public void visitFieldInsn(int op, String owner, String name, String fd) {
                    desc(fd);
                    if (!external(owner)) return;
                    stub(owner).fields.put(name, new String[]{fd, (op == Opcodes.GETSTATIC || op == Opcodes.PUTSTATIC) ? "1" : "0"});
                }
                @Override public void visitTypeInsn(int op, String t) { if (external(t)) stub(t); }
                @Override public void visitLdcInsn(Object v) { if (v instanceof Type t) type(t); }
                @Override public void visitInvokeDynamicInsn(String name, String d, Handle bsm, Object... a) {
                    if (!bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) return;
                    Type itfType = Type.getMethodType(d).getReturnType();
                    if (!external(itfType.getInternalName())) return;
                    Stub st = stub(itfType.getInternalName());
                    st.itf = true;
                    st.sam = name + "|" + ((Type) a[0]).getDescriptor();
                    desc(((Type) a[0]).getDescriptor());
                }
                @Override public void visitLocalVariable(String n, String ld, String s, Label a, Label b, int i) { desc(ld); }
            };
        }
        AnnotationVisitor ann(String d) {
            String n = Type.getType(d).getInternalName();
            if (!external(n)) return null;
            Stub st = stub(n); st.annotation = true;
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override public void visit(String name, Object value) {
                    String rd = value instanceof String ? "Ljava/lang/String;" : value instanceof Boolean ? "Z" : value instanceof Integer ? "I" : "Ljava/lang/Object;";
                    st.methods.add("0|" + name + "|()" + rd);
                }
                @Override public AnnotationVisitor visitArray(String name) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        String elem = "Ljava/lang/String;";
                        @Override public void visit(String n2, Object value) { if (value instanceof Type) elem = "Ljava/lang/Class;"; st.methods.add("0|" + name + "|()[" + elem); }
                        @Override public AnnotationVisitor visitAnnotation(String n2, String ad) { st.methods.add("0|" + name + "|()[" + ad); return ann(ad); }
                    };
                }
                @Override public AnnotationVisitor visitAnnotation(String name, String ad) { st.methods.add("0|" + name + "|()" + ad); return ann(ad); }
                @Override public void visitEnum(String name, String ed, String value) { st.methods.add("0|" + name + "|()" + ed); }
            };
        }
    }
}
