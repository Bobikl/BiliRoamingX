import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline inventory of original player fingerprint matches, never executed on a device. */
public class PlayerDexInventory {
    static Map<String,Object> method(Method m) {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("name",m.getName()); out.put("params",m.getParameterTypes());
        out.put("returns",m.getReturnType()); out.put("flags",m.getAccessFlags());
        List<String> ops = new ArrayList<>();
        if(m.getImplementation()!=null) for(Instruction i:m.getImplementation().getInstructions()) {
            String s=i.getOpcode().name;
            if(i instanceof ReferenceInstruction r) s+=" "+r.getReference();
            if(i instanceof WideLiteralInstruction w) s+=" #"+w.getWideLiteral();
            if(i instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload a) s+=" array="+a.getArrayElements();
            if(i instanceof FiveRegisterInstruction r) s+=" regs="+r.getRegisterC()+","+r.getRegisterD()+","+r.getRegisterE()+","+r.getRegisterF()+","+r.getRegisterG();
            else if(i instanceof ThreeRegisterInstruction r) s+=" regs="+r.getRegisterA()+","+r.getRegisterB()+","+r.getRegisterC();
            else if(i instanceof TwoRegisterInstruction r) s+=" regs="+r.getRegisterA()+","+r.getRegisterB();
            else if(i instanceof OneRegisterInstruction r) s+=" reg="+r.getRegisterA();
            ops.add(s);
        }
        out.put("ops",ops); return out;
    }
    public static void main(String[] args) throws Exception {
        List<String> needles=Files.readAllLines(Path.of(args[1]),StandardCharsets.UTF_8);
        Set<String> named=args.length>3?new HashSet<>(Files.readAllLines(Path.of(args[3]),StandardCharsets.UTF_8)):Set.of();
        Map<String,Object> results=new TreeMap<>();
        var container=DexFileFactory.loadDexContainer(new java.io.File(args[0]),Opcodes.getDefault());
        for(String dexName:container.getDexEntryNames()) for(ClassDef cls:container.getEntry(dexName).getDexFile().getClasses()) {
            List<String> matches=new ArrayList<>(); List<Method> methods=new ArrayList<>(); cls.getMethods().forEach(methods::add);
            if(named.contains(cls.getType()) || named.stream().anyMatch(n -> cls.getType().startsWith(n.substring(0, n.length()-1) + "$")))matches.add("RequestedTarget");
            if("Landroid/view/GestureDetector$SimpleOnGestureListener;".equals(cls.getSuperclass()) && methods.stream().anyMatch(m->m.getName().equals("onLongPress"))) matches.add("GestureListenerCandidate");
            if(cls.getType().equals("Lcom/bilibili/cron/Canvas;") || cls.getType().startsWith("Ltv/danmaku/ijk/media/player/IjkMediaAsset$MediaAssertSegment"))matches.add("NamedPlayerTarget");
            for(Method m:methods) if(m.getImplementation()!=null) {
                for(Instruction i:m.getImplementation().getInstructions()) if(i instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload a && a.getArrayElements().size()==6
                    && a.getArrayElements().stream().map(Number::intValue).collect(java.util.stream.Collectors.toSet()).equals(Set.of(1073741824,1069547520,1067450368,1065353216,1061158912,1056964608)))matches.add("SpeedArray @ "+m.getName());
                for(Instruction inst:m.getImplementation().getInstructions()) if(inst instanceof ReferenceInstruction r && r.getReference() instanceof StringReference s)
                    for(String needle:needles) if(!needle.isEmpty() && s.getString().contains(needle)) matches.add(needle+" @ "+m.getName()+m.getParameterTypes());
                if(m.getName().equals("<init>") && m.getParameterTypes().isEmpty() && "Ljava/lang/Object;".equals(cls.getSuperclass()) && cls.getInterfaces().size()==1
                    && methods.stream().filter(x->x.getParameterTypes().isEmpty()&&x.getReturnType().equals("F")).count()==2) {
                    var it=m.getImplementation().getInstructions().iterator(); List<String> first=new ArrayList<>();
                    for(int n=0;n<4&&it.hasNext();n++)first.add(it.next().getOpcode().name);
                    if(first.equals(List.of("invoke-direct","const/high16","invoke-static","move-result-object")))matches.add("PlaySpeedManagerImplFingerprint");
                }
            }
            if(matches.isEmpty())continue;
            Map<String,Object> out=new LinkedHashMap<>(); out.put("super",cls.getSuperclass());out.put("interfaces",cls.getInterfaces());out.put("matches",new LinkedHashSet<>(matches));
            List<String> fields=new ArrayList<>();cls.getFields().forEach(f->fields.add(f.getName()+":"+f.getType()+" flags="+f.getAccessFlags()));out.put("fields",fields);
            out.put("methods",methods.stream().map(PlayerDexInventory::method).toList());results.put(cls.getType(),out);
        }
        String json=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(results);
        Files.writeString(Path.of(args[2]),json,StandardCharsets.UTF_8);
        System.out.println("Matched "+results.size()+" classes");
    }
}
