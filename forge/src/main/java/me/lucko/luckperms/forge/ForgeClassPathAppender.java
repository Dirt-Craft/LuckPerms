/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.forge;

import cpw.mods.modlauncher.TransformingClassLoader;
import me.lucko.luckperms.common.plugin.classpath.ClassPathAppender;
import sun.misc.Launcher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class ForgeClassPathAppender implements ClassPathAppender {
    URLClassLoader urlClassLoader;
    Method addUrl;
    //https://github.com/MinecraftForge/MinecraftForge/blob/064ae6961b7875adc6f114f97f7ad0dc7a0b059c/src/fmllauncher/java/net/minecraftforge/fml/loading/FMLLoader.java#L212-L221
    @Override
    public void addJarToClasspath(Path file) {
        try {
            if (urlClassLoader == null) {
                TransformingClassLoader classLoader = (TransformingClassLoader) getClass().getClassLoader();
                Field field = ClassLoader.class.getDeclaredField("parent");
                field.setAccessible(true);
                this.urlClassLoader = (URLClassLoader) field.get(classLoader);
                addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addUrl.setAccessible(true);
            }
            addUrl.invoke(urlClassLoader, file.toUri().toURL());
        } catch (NoSuchFieldException | IllegalAccessException | MalformedURLException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
