import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
/** Jabel requires JavaCompiler's JavacTaskImpl, not the javac CLI's BasicJavacTask. */
public final class CompileLegacy {
    public static void main(String[] args) throws Exception {
        if(args.length<2)throw new IllegalArgumentException("Usage: CompileLegacy source-list compiler-options...");
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("A complete Java17 JDK is required");
        List<String> paths=new ArrayList<>();
        for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)) {
            line=line.trim();if(line.startsWith("\"")&&line.endsWith("\""))line=line.substring(1,line.length()-1);
            if(!line.isEmpty())paths.add(line);
        }
        // This is a whole-source compiler. Remove obsolete outputs after source
        // deletion/rename, without deleting resources sharing ForgeGradle's output.
        List<String> options=Arrays.asList(Arrays.copyOfRange(args,1,args.length));
        int outputOption=options.indexOf("-d");
        if(outputOption<0||outputOption+1>=options.size())throw new IllegalArgumentException("An explicit -d output directory is required");
        Path output=Paths.get(options.get(outputOption+1));
        if(Files.exists(output)) {
            try(Stream<Path> old=Files.walk(output)) {
                for(Path path:(Iterable<Path>)old.filter(path->Files.isRegularFile(path)&&path.toString().endsWith(".class"))::iterator)Files.delete(path);
            }
        }
        Files.createDirectories(output);
        if(paths.isEmpty())return;
        try(StandardJavaFileManager files=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)) {
            boolean ok=compiler.getTask(null,files,null,options,null,files.getJavaFileObjectsFromStrings(paths)).call();
            if(!ok)System.exit(1);
        }
    }
}
