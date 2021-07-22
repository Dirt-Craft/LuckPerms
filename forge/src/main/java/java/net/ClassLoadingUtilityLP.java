package java.net;

public class ClassLoadingUtilityLP {
    public static void addClassPathToClassLoader(URLClassLoader classLoader, URL url) {
        classLoader.addURL(url);
    }
}
