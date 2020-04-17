package teavm.gradle;

import org.gradle.api.*;
import org.gradle.api.artifacts.dsl.*;

import java.util.*;

public class TeaVMPlugin implements Plugin<Project>{
    static final String version = "0.7.0-dev-1087";

    @Override
    public void apply(Project project){
        project.apply(map("plugin", "java"));

        project.getConfigurations().create("teavmsources");

        DependencyHandler d = project.getDependencies();

        d.add("compile", "org.teavm:teavm-classlib:" + version);
        d.add("compile", "org.teavm:teavm-jso:" + version);
        d.add("compile", "org.teavm:teavm-jso-apis:" + version);
        d.add("teavmsources", "org.teavm:teavm-platform:" + version + ":sources");
        d.add("teavmsources", "org.teavm:teavm-classlib:" + version + ":sources");
        d.add("teavmsources", "org.teavm:teavm-jso:" + version + ":sources");
        d.add("teavmsources", "org.teavm:teavm-jso-apis:" + version + ":sources");

        project.task(map(
            Task.TASK_TYPE, TeaVMTask.class,
            Task.TASK_DEPENDS_ON, "build",
            Task.TASK_DESCRIPTION, "TeaVM Compile",
            Task.TASK_GROUP, "build"
        ), "teavmc");
    }

    private Map<String, String> map(Object... keys){
        HashMap<String, String> out = new HashMap<>();
        for(int i = 0; i < keys.length; i += 2) out.put((String)keys[i], (String)keys[i + 1]);
        return out;
    }
}
