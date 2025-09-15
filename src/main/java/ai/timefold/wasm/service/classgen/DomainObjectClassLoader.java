package ai.timefold.wasm.service.classgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DomainObjectClassLoader extends ClassLoader {
    private final Map<String, byte[]> classNameToBytecode;

    public DomainObjectClassLoader() {
        super(Thread.currentThread().getContextClassLoader());
        this.classNameToBytecode = new HashMap<>();
    }

    public void addClass(String className, byte[] bytecode) {
        classNameToBytecode.put(className, bytecode);
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            if (classNameToBytecode.containsKey(name)) {
                var bytecode = classNameToBytecode.get(name);
                return defineClass(name, bytecode, 0, bytecode.length);
            }
            throw e;
        }
    }

    public Class<?> getClassForDomainClassName(String className) {
        try {
            return loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void dumpGeneratedClasses(Path generatedClassPath) {
        try {
            Files.createDirectories(generatedClassPath);
            for (var entry : classNameToBytecode.entrySet()) {
                Files.write(generatedClassPath.resolve(Path.of(entry.getKey() + ".class")),
                            entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
