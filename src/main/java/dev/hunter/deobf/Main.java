package dev.hunter.deobf;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.plugins.input.java.JavaInputPlugin;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("Starting analysing jar file...");
        OptionParser optionParser = new OptionParser();

        OptionSpec<String> inputArg = optionParser.accepts("input").withRequiredArg().ofType(String.class);
        OptionSpec<String> outputArg = optionParser.accepts("output").withRequiredArg().ofType(String.class);
        OptionSpec<String> mappings = optionParser.accepts("mappings").withRequiredArg().ofType(String.class);
        OptionSpec<Boolean> decompile = optionParser.accepts("decompile").withRequiredArg().ofType(Boolean.class);

        OptionSet options = optionParser.parse(args);

        Path input = Paths.get(getOption(options, inputArg));
        Path output = Paths.get(getOption(options, outputArg));
        Path mappingsPath = YarnDownloading.resolve(getOption(options, mappings));

        /*Verify extension*/
        String outputName = output.getFileName().toString();
        int lastIndex = outputName.lastIndexOf('.');

        output = output.resolveSibling(lastIndex == -1 ? outputName + ".jar" : outputName.substring(0, lastIndex) + ".jar");

        if (output.toFile().exists()) output.toFile().delete();

        try {
            System.out.println("Attempting apply yarn class mapping...");

            TinyRemapper remapper = TinyRemapper.newRemapper()
                    .withMappings(TinyUtils.createTinyMappingProvider(mappingsPath, "intermediary", "named"))
                    .renameInvalidLocals(true)
                    .rebuildSourceFilenames(true)
                    .ignoreConflicts(true)
                    .keepInputData(true)
                    .skipLocalVariableMapping(true)
                    .ignoreFieldDesc(true)
                    .build();

            OutputConsumerPath outputConsumer = new OutputConsumerPath(output);
            outputConsumer.addNonClassFiles(input);
            remapper.readInputs(input);

            remapper.readClassPath(input);
            remapper.apply(outputConsumer);
            remapper.finish();

            outputConsumer.close();

            System.out.println("Successfully mapped classpath");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Path mappingsTiny2 = YarnDownloading.resolveTiny2(getOption(options, mappings));

        try {
            System.out.println("Attempting apply yarn field/methods reference...");
            Map<String, String> mapping = RemapUtil.getMappings(mappingsTiny2); //Generate mappings

            RemapUtil.remapJar(output, mapping); //Apply mappings
            System.out.println("Successfully apply field/methods reference");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Clean up
        Files.delete(mappingsPath);
        Files.delete(mappingsTiny2);
        Files.delete(YarnDownloading.path);

        if (getOption(options, decompile)) {
            decompileOutPut(output);
        }

        System.out.println("Finished all tasks!");
    }

    private static void decompileOutPut(Path output) {
        File outputDir = new File(output.toFile().getAbsolutePath().replaceAll(".jar|.zip", ""));

        JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.setCfgOutput(false);
        jadxArgs.setOutDir(outputDir);
        jadxArgs.setInputFile(output.toFile());
        jadxArgs.setInlineAnonymousClasses(true);
        jadxArgs.setInlineMethods(false);
        jadxArgs.setExtractFinally(false);
        jadxArgs.setUseKotlinMethodsForVarNames(JadxArgs.UseKotlinMethodsForVarNames.DISABLE);
        jadxArgs.setInlineAnonymousClasses(false);
        jadxArgs.setDebugInfo(false);

        try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
            JavaInputPlugin javaPlugin = new JavaInputPlugin();
            jadx.registerPlugin(javaPlugin);
            jadx.load();
            jadx.save();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @SuppressWarnings("all")
    private static <T> T getOption(OptionSet optionSet, OptionSpec<T> optionSpec) {
        try {
            return optionSet.valueOf(optionSpec);
        } catch (Throwable throwable) {
            throw throwable;
        }
    }
}