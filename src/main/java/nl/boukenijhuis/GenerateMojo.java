package nl.boukenijhuis;

import nl.boukenijhuis.assistants.AIAssistant;
import nl.boukenijhuis.assistants.chatgpt.ChatGpt;
import nl.boukenijhuis.assistants.ollama.Ollama;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

@Mojo(name = "generate", requiresDependencyResolution = ResolutionScope.TEST)
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "")
    private String testFilePath;

    @Parameter(defaultValue = "http://localhost:11434")
    private String server;

    @Parameter(defaultValue = "/api/generate")
    private String url;

    @Parameter(defaultValue = "ollama")
    private String family;

    @Parameter(defaultValue = "pxlksr/opencodeinterpreter-ds")
    private String model;

    @Parameter
    private String apiKey;

    @Parameter
    private String prompt;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {

        getLog().info("Generating an implementation for: " + testFilePath);

        try {
            List<String> jarFiles = updateClassLoader();

            AIAssistant aiAssistant = createAssistant();

            // jarfiles are necessary for the compilation
            Generator generator = new Generator(jarFiles);

            String[] args = {testFilePath};
            boolean result = generator.run(aiAssistant, new TestRunner(), args);

            if (!result) {
                throw new MojoExecutionException("No solution found");
            }

        } catch (IOException | DependencyResolutionRequiredException e) {
            e.printStackTrace(System.out);
            getLog().info(e.getMessage());
            throw new MojoExecutionException(e.getMessage());
        }


    }

    private AIAssistant createAssistant() {
        Properties properties = new Properties();

        if (family.equalsIgnoreCase("chatgpt")) {
            properties.setProperty(family + ".server", "https://api.openai.com");
            properties.setProperty(family + ".url", "/v1/chat/completions");
            properties.setProperty(family + ".maxTokens", "600");
            properties.setProperty(family + ".api-key", apiKey);
        } else {
            properties.setProperty(family + ".server", server);
            properties.setProperty(family + ".url", url);
        }
        properties.setProperty(family + ".model", model);
        properties.setProperty(family + ".timeout", "30");

        if (prompt != null) {
            // check if the prompt contains a '%s'
            if (!prompt.contains("%s")) {
                getLog().info("!!! The provided prompt does NOT contain '%s': [" + prompt + "].");
            }
            properties.setProperty(family + ".prompt", prompt);

        }

        AIAssistant assistant;
        if (family.equalsIgnoreCase("chatgpt")){
            assistant = new ChatGpt(properties);
        } else {
             assistant = new Ollama(properties);
        }
        return assistant;
    }

    private List<String> updateClassLoader() throws DependencyResolutionRequiredException {
        // get the dependencies from the outer pom (the pom which uses this plugin)
        List<String> jarFiles = project.getTestClasspathElements();
        ;

        // directories NEED a slash at the end
        List<URL> urls = jarFiles.stream().map(x -> {
            try {
                if (x.endsWith(".jar")) {
                    return new URL("file://" + x);
                } else {
                    // for directories
                    return new URL("file://" + x + "/");
                }
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        }).toList();

        // update the classloader
        ClassRealm contextClassLoader = (ClassRealm) Thread.currentThread().getContextClassLoader();
        ClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), contextClassLoader);
        Thread.currentThread().setContextClassLoader(cl);
        return jarFiles;
    }
}