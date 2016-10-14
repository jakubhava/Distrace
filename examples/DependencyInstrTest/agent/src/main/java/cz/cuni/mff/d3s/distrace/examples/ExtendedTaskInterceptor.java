package cz.cuni.mff.d3s.distrace.examples;


import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.concurrent.Callable;

public class ExtendedTaskInterceptor {

    public static String instrument(@SuperCall(serializableProxy=true) Callable<String> value) throws Exception{
            return "Instrumented by Extended: (" + value.call() + ")";
    }
}
