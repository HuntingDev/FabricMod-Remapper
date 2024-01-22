package dev.hunter.deobf;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class RemapUtil {
    public static void remapJar(Path outputJar, Map<String, String> mappings) throws IOException {
        Path tempOutputJar = Paths.get(outputJar.toString() + "_temp");

        try (ZipInputStream inputZip = new ZipInputStream(Files.newInputStream(outputJar));
             ZipOutputStream tempOutputZip = new ZipOutputStream(Files.newOutputStream(tempOutputJar))) {

            ZipEntry entry;
            while ((entry = inputZip.getNextEntry()) != null) {
                String entryName = entry.getName();

                //Remember other files
                if (!entryName.endsWith(".class")) {
                    tempOutputZip.putNextEntry(new ZipEntry(entryName));
                    byte[] buffer = readStream(inputZip);
                    tempOutputZip.write(buffer);
                    tempOutputZip.closeEntry();
                    continue;
                }

                byte[] classBytes = readStream(inputZip); //Read current class
                byte[] remappedBytes = remapClass(classBytes, mappings); //Remap current class

                //Remember modified class
                tempOutputZip.putNextEntry(new ZipEntry(entryName));
                tempOutputZip.write(remappedBytes);
                tempOutputZip.closeEntry();
            }
        }

        Files.move(tempOutputJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
    }


    private static byte[] readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }


    //Todo: make a check about owners?
    private static byte[] remapClass(byte[] classBytes, Map<String, String> mappings) {
        ClassReader classReader = new ClassReader(classBytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        classNode.fields.parallelStream().filter(fieldNode -> mappings.containsKey(fieldNode.name)).forEach(fieldNode -> {
            System.out.println("Find nasty field. Renaming \"" + fieldNode.name + "\" to \"" + mappings.get(fieldNode.name) + "\"");
            fieldNode.name = mappings.get(fieldNode.name);
        });

        classNode.methods.parallelStream().filter(methodNode -> mappings.containsKey(methodNode.name)).forEach(methodNode -> {
            System.out.println("Find nasty method. Renaming \"" + methodNode.name + "\" to \"" + mappings.get(methodNode.name) + "\"");
            methodNode.name = mappings.get(methodNode.name);
        });

        for (MethodNode method : classNode.methods) {
            InsnList list = method.instructions;
            Arrays.stream(list.toArray()).parallel().forEach(currentInsn -> {
                if (currentInsn instanceof MethodInsnNode) {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) currentInsn;

                    //Detect nasty method
                    if (mappings.containsKey(methodInsnNode.name)) {
                        System.out.println("Find nasty method call. Renaming \"" + methodInsnNode.name + "\" to \"" + mappings.get(methodInsnNode.name) + "\"");
                        methodInsnNode.name = mappings.get(methodInsnNode.name);
                    }
                } else if (currentInsn instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsnNode = (FieldInsnNode) currentInsn;

                    //Detect nasty field
                    if (mappings.containsKey(fieldInsnNode.name)) {
                        System.out.println("Find nasty field call. Renaming \"" + fieldInsnNode.name + "\" to \"" + mappings.get(fieldInsnNode.name) + "\"");
                        fieldInsnNode.name = mappings.get(fieldInsnNode.name);
                    }
                } else if (currentInsn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode dynamicInsnNode = (InvokeDynamicInsnNode) currentInsn;

                    //Detect nasty invoke dynamic
                    if (mappings.containsKey(dynamicInsnNode.name)) {
                        System.out.println("Find nasty invoke dynamic. Renaming \"" + dynamicInsnNode.name + "\" to \"" + mappings.get(dynamicInsnNode.name) + "\"");
                        dynamicInsnNode.name = mappings.get(dynamicInsnNode.name);
                    }

                    if (mappings.containsKey(dynamicInsnNode.bsm.getName())) {
                        System.out.println("Find nasty bsm. Renaming \"" + dynamicInsnNode.bsm.getName() + "\" to \"" + mappings.get(dynamicInsnNode.bsm.getName()) + "\"");
                        Handle handle = dynamicInsnNode.bsm;
                        InvokeDynamicInsnNode newDynamicInsnNode = new InvokeDynamicInsnNode(dynamicInsnNode.name, dynamicInsnNode.desc,
                                new Handle(handle.getTag(), handle.getOwner(), mappings.get(dynamicInsnNode.bsm.getName()), handle.getDesc()), dynamicInsnNode.bsmArgs);
                        method.instructions.insertBefore(currentInsn, newDynamicInsnNode);
                        method.instructions.remove(currentInsn);
                    }
                } else if (currentInsn instanceof LdcInsnNode && ((LdcInsnNode) currentInsn).cst instanceof String) { //String constants
                    LdcInsnNode ldcInsnNode = (LdcInsnNode) currentInsn;
                    String cst = (String) ldcInsnNode.cst;

                    if (mappings.containsKey(cst)) {
                        System.out.println("Find nasty string constant. Renaming \"" + cst + "\" to \"" + mappings.get(cst) + "\"");
                        ldcInsnNode.cst = mappings.get(cst);
                    }
                }
            });
        }

        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    public static Map<String, String> getMappings(Path path) throws IOException {
        Map<String, String> map = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.replaceAll("\\s+", " "); //Inline mappings
            if (line.startsWith(" ")) line = line.substring(1);

            String[] lines = line.split(" "); //Split line


            if (lines.length < 4 || lines[0].startsWith("c") || lines[0].startsWith("p") || lines[3].startsWith("<") || lines[2].equals(lines[3]))
                continue;

            map.put(lines[2], lines[3]); //Add appropriate mapping
        }

        reader.close();

        return map;
    }

}
