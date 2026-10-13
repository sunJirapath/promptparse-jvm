import io.github.sunjirapath.promptparse.PromptPay;
import io.github.sunjirapath.promptparse.example.Example;
import java.io.File;
import java.util.zip.ZipFile;
import kotlin.KotlinVersion;

/** A consumer of only the generated Maven artifact and its transitive POM dependencies. */
public final class ArtifactSmokeTest {
  private ArtifactSmokeTest() {}

  public static void main(String[] args) throws Exception {
    File jar =
        new File(PromptPay.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    if (!jar.isFile() || !jar.getName().endsWith(".jar")) {
      throw new AssertionError("library must be loaded from the published JAR: " + jar);
    }
    try (ZipFile zip = new ZipFile(jar)) {
      if (zip.getEntry("META-INF/LICENSE") == null || zip.getEntry("META-INF/NOTICE") == null) {
        throw new AssertionError("published JAR is missing license or attribution");
      }
    }
    // No direct stdlib dependency: this compiles and runs only when the POM supplies it.
    System.out.println("POM supplied Kotlin stdlib " + KotlinVersion.CURRENT);
    Example.main(args);
  }
}
