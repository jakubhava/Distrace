package water;

import cz.cuni.mff.d3s.distrace.examples.SumMRTask;
import cz.cuni.mff.d3s.distrace.instrumentation.StorageUtils;
import cz.cuni.mff.d3s.distrace.instrumentation.StackTraceUtils;
import cz.cuni.mff.d3s.distrace.tracing.TraceContext;
import jsr166y.CountedCompleter;
import net.bytebuddy.asm.Advice;


public class RPCAdvices {

    /**
     * This method is used to open an span representing non-empty remote computation
     */
    public static class call {
        @Advice.OnMethodEnter
        public static void enter(@Advice.This RPC thizz) {
            if (StackTraceUtils.containsMethodCall("remote_compute")) {
                if (thizz._dt instanceof SumMRTask) {
                    TraceContext tc = TraceContext.getFromObject(thizz._dt).deepCopy();
                    tc.openNestedSpan("H2O Node" + H2O.SELF.index() + " - Remote work - rpc")
                            .setIpPort(H2O.getIpPortString())
                            .add("ipPort", H2O.getIpPortString());
                    StorageUtils.addToList("remote_task_num", thizz._tasknum);
                    tc.attachOnObject(thizz._dt);
                }
            }
        }
    }

    /**
     * This method closes the span created by the previous method
     */
    public static class response {
        @Advice.OnMethodExit
        public static void exit(@Advice.This RPC thizz) {
            if (thizz._dt instanceof SumMRTask) {
                if (StorageUtils.getList("remote_task_num").contains(thizz._tasknum)) {
                    StorageUtils.getList("remote_task_num").remove(thizz._tasknum);
                    TraceContext tc = TraceContext.getFromObject(thizz._dt);
                    tc.closeCurrentSpan();
                }
            }
        }
    }

}
