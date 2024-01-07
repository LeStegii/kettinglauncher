package org.kettingpowered.launcher.dependency;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentClassLoader extends URLClassLoader {

    public AgentClassLoader(URL[] libs){
        super(libs);
    }

    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c;
        c = findLoadedClass(name);
        if (c != null) return c;
        if (super.findResource(name.replace('.', '/').concat(".class")) != null){
            c = super.findClass(name);
        }else {
            c = super.loadClass(name, resolve);
        }
        if (resolve) resolveClass(c);
        return c;
    }
}
