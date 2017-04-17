package cz.cuni.mff.d3s.distrace.examples;


import cz.cuni.mff.d3s.distrace.Instrumentor;
import cz.cuni.mff.d3s.distrace.instrumentation.BaseAgentBuilder;
import cz.cuni.mff.d3s.distrace.instrumentation.CustomAgentBuilder;
import cz.cuni.mff.d3s.distrace.instrumentation.TransformerUtils;
import cz.cuni.mff.d3s.distrace.utils.ReflectionUtils;
import javassist.ClassPool;
import javassist.ClassPoolInterceptor;
import jsr166y.CountedCompleter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import water.*;
import water.fvec.Frame;

import java.lang.reflect.Method;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class Starter {
    public static void main(String args[]){
        new Instrumentor().start(args, new CustomAgentBuilder() {
            @Override
            public AgentBuilder createAgent(BaseAgentBuilder builder, String pathToGeneratedClasses) {
                return builder
                        .type(is(ClassPool.class))
                        .transform(TransformerUtils.forMethodsIn(new ClassPoolInterceptor(pathToGeneratedClasses)))
                        .type(isSubTypeOf(CountedCompleter.class))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
                                Method __tryComplete = ReflectionUtils.findMethod(CountedCompleter.class, "__tryComplete", CountedCompleter.class);
                                return builder; //.visit(Advice.to(CountedCompleterAdvice.__tryComplete.class).on(is(__tryComplete)));
                            }

                        })
                        .type(isSubTypeOf(H2O.H2OCountedCompleter.class))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
                                Method dfork = ReflectionUtils.findMethod(MRTask.class, "dfork", byte[].class, Frame.class, boolean.class);
                                Method dfork2 = ReflectionUtils.findMethod(MRTask.class, "dfork", Key[].class);
                                Method getResult = ReflectionUtils.findMethod(MRTask.class, "getResult", boolean.class);
                                Method remote_compute = ReflectionUtils.findMethod(MRTask.class, "remote_compute", int.class, int.class);
                                Method onCompletion = ReflectionUtils.findMethod(MRTask.class, "onCompletion", CountedCompleter.class);
                                Method reduce2 = ReflectionUtils.findMethod(MRTask.class, "reduce2", MRTask.class);
                                return  TransformerUtils.defineTraceId(builder).
                                            visit(Advice.to(MRTaskAdvices.setupLocal0.class).on(named("setupLocal0"))).
                                            visit(Advice.to(MRTaskAdvices.remote_compute.class).on(is(remote_compute))).
                                            visit(Advice.to(MRTaskAdvices.compute2.class).on(named("compute2"))).
                                            visit(Advice.to(MRTaskAdvices.onCompletion.class).on(is(onCompletion))).
                                            visit(Advice.to(MRTaskAdvices.dfork.class).on(anyOf(dfork, dfork2))).
                                            visit(Advice.to(MRTaskAdvices.reduce2.class).on(is(reduce2))).
                                            visit(Advice.to(MRTaskAdvices.map.class).on(named("map"))).
                                            visit(Advice.to(MRTaskAdvices.getResult.class).on(is(getResult)));

                            }
                        })
                        .type(is(RPC.class))
                        .transform(new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
                                Method call = ReflectionUtils.findMethod(RPC.class, "call");

                                return  builder.visit(Advice.to(RPCAdvices.call.class).on(is(call))).
                                        visit(Advice.to(RPCAdvices.response.class).on(named("response")));
                            }
                        });
                }
        });
    }

}
