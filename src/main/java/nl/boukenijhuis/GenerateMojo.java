package nl.boukenijhuis;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.ArrayList;

@Mojo(name = "generate", requiresDependencyResolution = ResolutionScope.TEST)
public class GenerateMojo extends AbstractMojo {

    @Parameter
    private String testFilePath;

    @Parameter
    private String server;

    @Parameter
    private String url;

    @Parameter
    private String family;

    @Parameter
    private String model;

    @Parameter
    private String maxTokens;

    @Parameter
    private String timeout;

    @Parameter
    private String prompt;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {

        if (testFilePath == null) {
            throw new MojoExecutionException("Missing test file path");
        }

        getLog().info("Generating an implementation for: " + testFilePath);

        try {

            // add all possible configuration values
            ArgumentList argumentList = new ArgumentList();
            argumentList.addIfValueIsNotNull("--test-file", testFilePath);
            argumentList.addIfValueIsNotNull("--server", server);
            argumentList.addIfValueIsNotNull("--url", url);
            argumentList.addIfValueIsNotNull("--family", family);
            argumentList.addIfValueIsNotNull("--model", model);
            argumentList.addIfValueIsNotNull("--max-tokens", maxTokens);
            argumentList.addIfValueIsNotNull("--timeout", timeout);


            // a prompt should contain %s, which will be replaced by the contents of the testfile
            if (prompt != null) {
                // check if the prompt contains a '%s'
                if (!prompt.contains("%s")) {
                    getLog().info("!!! The provided prompt does NOT contain '%s': [" + prompt + "].");
                }
                argumentList.addIfValueIsNotNull("--prompt", prompt);
            }

            // jarfiles are necessary for the compilation
            Generator generator = new Generator(project.getTestClasspathElements());

            String[] args = argumentList.toArray(new String[0]);
            boolean result = generator.runGenerator(args);

            if (!result) {
                throw new MojoExecutionException("No solution found");
            }

        } catch (IOException | DependencyResolutionRequiredException e) {
            e.printStackTrace(System.out);
            getLog().info(e.getMessage());
            throw new MojoExecutionException(e.getMessage());
        }
    }

    static class ArgumentList extends ArrayList<String> {
        public void addIfValueIsNotNull(String key, String value) {
            if (value != null) {
                add(key);
                add(value);
            }
        }
    }
}