package teavm.gradle;

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

    @TaskAction
    public void compTeaVM(){
        TeaVMTool tool = new TeaVMTool();
        Project project = getProject();

        tool.setTargetDirectory(new File(installDirectory));
        tool.setTargetFileName(targetFileName);

        if(project.hasProperty("mainClassName") && project.property("mainClassName") != null){
            tool.setMainClass((String)project.property("mainClassName"));
        }else throw new RuntimeException("mainClassName not found!");

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
        tool.setSourceMapsFileGenerated(generateSourceMap);

        ClassLoader classLoader = prepareClassLoader();
        try{
            tool.setClassLoader(classLoader);
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

    private URLClassLoader prepareClassLoader(){
        try{
            Configuration it = getProject().getConfigurations().getByName("runtime");

            List<URL> urls = new ArrayList<>();
            for(File file : it.getFiles()) urls.add(file.toURI().toURL());
            for(File file : it.getAllArtifacts().getFiles()) urls.add(file.toURI().toURL());

            return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        }catch(MalformedURLException e){
            throw new GradleException("Error gathering classpath information", e);
        }
    }
}