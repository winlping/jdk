/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.lang;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/*
 * Class to track and run user level shutdown hooks registered through
 * {@link Runtime#addShutdownHook Runtime.addShutdownHook}.
 *
 * @see java.lang.Runtime#addShutdownHook
 * @see java.lang.Runtime#removeShutdownHook
 */

class ApplicationShutdownHooks {
    /* The set of registered hooks */
    private static IdentityHashMap<Thread, Thread> hooks; // 随 Runtime 类型调用addShutdown方法后被加载
    static {// 加载后执行该静态代码块，向Shutdown类中添加一个钩子，该钩子包含了 ApplicationShutdownHooks 中的 hooks 中的钩子线程
        try {
            Shutdown.add(1 /* shutdown hook invocation order */,
                false /* not registered if shutdown in progress */,
                new Runnable() {
                    public void run() {
                        runHooks();
                    }
                }
            );
            hooks = new IdentityHashMap<>();// 该结构用于存储 Runtime.addShutdownHook中的线程，在执行钩子函数时，会回调 Shutdown 类型中的 hooks, 按照 hooks 数组中的钩子执行，其中 下标为 1的钩子
        } catch (IllegalStateException e) {// 执行了ApplicationShutdownHooks.runHooks 方法，该方法，执行了添加到 ApplicationShutdownHooks.hooks中的线程
            // application shutdown hooks cannot be added if
            // shutdown is in progress.
            hooks = null;
        }
    }


    private ApplicationShutdownHooks() {}

    /* Add a new shutdown hook.  Checks the shutdown state and the hook itself,
     * but does not do any security checks.
     */
    static synchronized void add(Thread hook) {
        if(hooks == null)
            throw new IllegalStateException("Shutdown in progress");

        if (hook.isAlive())
            throw new IllegalArgumentException("Hook already running");

        if (hooks.containsKey(hook))
            throw new IllegalArgumentException("Hook previously registered");

        hooks.put(hook, hook);
    }

    /* Remove a previously-registered hook.  Like the add method, this method
     * does not do any security checks.
     */
    static synchronized boolean remove(Thread hook) {
        if(hooks == null)
            throw new IllegalStateException("Shutdown in progress");

        if (hook == null)
            throw new NullPointerException();

        return hooks.remove(hook) != null;
    }

    /* Iterates over all application hooks creating a new thread for each
     * to run in. Hooks are run concurrently and this method waits for
     * them to finish.
     */
    static void runHooks() {
        Collection<Thread> threads;
        synchronized(ApplicationShutdownHooks.class) {
            threads = hooks.keySet();
            hooks = null;
        }
        for (Thread hook : threads) {
            try {
                hook.start();
            } catch (IllegalThreadStateException ignore) {
                // already started
            } catch (RejectedExecutionException ignore) {
                // scheduler shutdown?
                assert hook.isVirtual();
            }
        }
        for (Thread hook : threads) {
            while (true) {
                try {
                    hook.join(); // 启动所有线程后等待所有线程运行结束
                    break;
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}
