package nl.boukenijhuis;

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

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {

        getLog().info("Generating an implementation for: " + testFilePath);

        try {
            // get the dependencies from the outer pom (the pom which uses this plugin)
            List<String> jarFiles = project.getTestClasspathElements()
                    // TODO: remove logback hack?
                    .stream().filter(x -> !x.contains("logback")).toList();

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
            for (URL url : urls) {
                contextClassLoader.addURL(url);
            }

            Properties properties = new Properties();
            properties.setProperty(family + ".server", server);
            properties.setProperty(family + ".url", url);
            properties.setProperty(family + ".model", model);

            Ollama aiAssistant = new Ollama(properties);

            // jarfiles are necessary for the compilation
            Generator generator = new Generator(jarFiles);

            String[] args = {testFilePath};
            generator.run(aiAssistant, new TestRunner(), args);

        } catch (IOException | DependencyResolutionRequiredException e) {
            e.printStackTrace(System.out);
            getLog().info(e.getMessage());
            throw new MojoExecutionException(e.getMessage());
        }


    }
}