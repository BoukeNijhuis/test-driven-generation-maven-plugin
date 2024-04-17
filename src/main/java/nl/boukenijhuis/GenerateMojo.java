package nl.boukenijhuis;

import nl.boukenijhuis.assistants.AIAssistant;
import nl.boukenijhuis.assistants.chatgpt.ChatGpt;
import nl.boukenijhuis.assistants.ollama.Ollama;
import nl.boukenijhuis.dto.ArgumentContainer;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
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

    @Parameter(defaultValue = "30")
    private String timeout;

    @Parameter
    private String apiKey;

    @Parameter
    private String prompt;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {

        getLog().info("Generating an implementation for: " + testFilePath);

        try {
            AIAssistant aiAssistant = createAssistant();

            // jarfiles are necessary for the compilation
            Generator generator = new Generator(project.getTestClasspathElements());

            String[] args = {testFilePath};
            boolean result = generator.run(aiAssistant, new TestRunner(), new ArgumentContainer(args));

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

            // read the OpenAI API from the environment
            String openAIApiKey = System.getenv("OPENAI_API_KEY");
            if (openAIApiKey != null) {
                properties.setProperty(family + ".api-key", openAIApiKey);
            }
        } else {
            properties.setProperty(family + ".server", server);
            properties.setProperty(family + ".url", url);
        }
        properties.setProperty(family + ".model", model);
        properties.setProperty(family + ".timeout", timeout);

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
}