package teavm.gradle;

import groovy.lang.*;
import org.gradle.api.*;
import org.gradle.api.artifacts.*;
import org.gradle.api.plugins.*;
import org.gradle.api.tasks.*;
import org.teavm.diagnostics.*;
import org.teavm.tooling.*;
import org.teavm.tooling.sources.*;
import org.teavm.vm.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class TeaVMTask extends DefaultTask{
    public String installDirectory = new File(getProject().getBuildDir(), "teavm").getAbsolutePath();
    public String targetFileName = "app.js";
    public boolean copySources = false;
    public boolean generateSourceMap = false;
    public boolean obfuscate = true;
    public boolean incremental = true;
    public TeaVMOptimizationLevel optimization = TeaVMOptimizationLevel.ADVANCED;
    public TeaVMTargetType target = TeaVMTargetType.JAVASCRIPT;
    public Closure<TeaVMTool> config;
    public String mainClass;

    @TaskAction
    public void compTeaVM(){
        TeaVMTool tool = new TeaVMTool();
        Project project = getProject();

        tool.setTargetDirectory(new File(installDirectory));
        tool.setTargetFileName(targetFileName);

        Consumer<File> addSrc = f -> {
            if(f.isFile()){
                if(f.getAbsolutePath().endsWith(".jar")){
                    tool.addSourceFileProvider(new JarSourceFileProvider(f));
                }else{
                    tool.addSourceFileProvider(new DirectorySourceFileProvider(f));
                }
            }else{
                tool.addSourceFileProvider(new DirectorySourceFileProvider(f));
            }
        };

        JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);

        convention
            .getSourceSets()
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            .getAllSource()
            .getSrcDirs().forEach(addSrc);

        project
            .getConfigurations()
            .getByName("teavmsources")
            .getFiles()
            .forEach(addSrc);

        File cacheDirectory = new File(project.getBuildDir(), "teavm-cache");
        cacheDirectory.mkdirs();
        tool.setCacheDirectory(cacheDirectory);
        tool.setObfuscated(obfuscate);
        tool.setOptimizationLevel(optimization);
        tool.setIncremental(incremental);
        tool.setSourceFilesCopied(copySources);
        tool.setTargetType(target);
        tool.setSourceMapsFileGenerated(generateSourceMap);
        if(mainClass != null){
            tool.setMainClass(mainClass);
        }else{
            throw new RuntimeException("mainClass not defined!");
        }

        if(config != null){
            config.call(tool);
        }

        try{
            Configuration it = getProject().getConfigurations().getByName("runtime");


            List<URL> urls = new ArrayList<>();
            for(File file : new File(project.getBuildDir(), "libs").listFiles()) urls.add(file.toURI().toURL());
            for(File file : it.getFiles()) urls.add(file.toURI().toURL());
            for(File file : it.getAllArtifacts().getFiles()) urls.add(file.toURI().toURL());

            tool.setClassLoader(new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader()));
            tool.generate();

            String issues = tool.getProblemProvider().getProblems().stream().map(p -> {
                DefaultProblemTextConsumer cons = new DefaultProblemTextConsumer();
                p.render(cons);
                return cons.getText();
            }).collect(Collectors.joining("\n"));

            if(!issues.isEmpty()){
                System.out.println("Issues:\n" + issues);
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
