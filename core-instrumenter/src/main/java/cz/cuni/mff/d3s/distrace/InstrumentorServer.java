package cz.cuni.mff.d3s.distrace;

import cz.cuni.mff.d3s.distrace.utils.BaseAgentBuilder;
import cz.cuni.mff.d3s.distrace.utils.InstrumentorClassLoader;
import cz.cuni.mff.d3s.distrace.utils.CustomAgentBuilder;
import nanomsg.exceptions.IOException;
import nanomsg.pair.PairSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class InstrumentorServer {
    private static final Logger log = LogManager.getLogger(InstrumentorServer.class);
    public static HashMap<String, Boolean> classAvailabilityMap = new HashMap<>();
    private static final byte REQ_TYPE_INSTRUMENT = 0;
    private static final byte REQ_TYPE_STOP = 1;
    private static final byte REQ_TYPE_CHECK_HAS_CLASS = 2;
    private PairSocket sock;
    private String sockAddr;
    private ClassFileTransformer transformer;
    private CustomAgentBuilder builder;
    InstrumentorClassLoader instLoader = new InstrumentorClassLoader();

    InstrumentorServer(String sockAddr, CustomAgentBuilder builder) {
        this.sockAddr = sockAddr;
        this.builder = builder;
    }

    private void handleHasClassCheck() {
        byte[] classNameSlashes = sock.recvBytes();
        String classNameDots = Utils.convertToJavaName(new String(classNameSlashes, StandardCharsets.UTF_8));


        log.info("Checking whether class is available " + classNameDots);

        try {
            // use current classloader for this check, not InstrumentorClassLoader since that
            // would cause unwanted behaviour and other necessary checks
            this.getClass().getClassLoader().loadClass(classNameDots);
            log.info("Instrumentor contains class " + classNameDots);
            classAvailabilityMap.put(classNameDots, true);
            sock.send("yes");
        } catch (ClassNotFoundException e) {
            log.info("Instrumentor does not contain class " + classNameDots);
            classAvailabilityMap.put(classNameDots, false);
            sock.send("no");
        }

    }

    private void handleInstrument() {
        byte[] classNameSlashes = sock.recvBytes();
        String classNameDots = Utils.convertToJavaName(new String(classNameSlashes, StandardCharsets.UTF_8));

        if (classAvailabilityMap.get(classNameDots)) {
            instrument(classNameDots);
        } else {
            byte[] bytes = sock.recvBytes();
            log.debug("Received class code from native agent: " + classNameDots);
            instLoader.registerByteCode(classNameDots, bytes);
            log.debug("Registered class bytecode into Instrumentor class loader");
            instrument(classNameDots);
        }
    }

    private void instrument(String className) {
        // we do not have to provide bytecode as parameter to transform method since it is fetched when needed by our class file locator
        // implemented using byte code class loader

        // it returns null in case the class shouldn't have been transformed
        byte[] transformed = new byte[0];
        try {
            transformed = transformer.transform(instLoader, className, null, null, null);
        } catch (IllegalClassFormatException e) {
            log.error("Invalid bytecode for class " + className);
            sock.send("ERROR_INVALID_FORMAT");
        }

        if (transformed != null) { // the class was transformed
            sock.send(transformed.length + ""); // send length of instrumented code
            sock.send(transformed); // send instrumented bytecode
        }
    }

    private void handleRequest(byte requestType) {
        switch (requestType) {
            case REQ_TYPE_INSTRUMENT:
                handleInstrument();
                break;
            case REQ_TYPE_CHECK_HAS_CLASS:
                handleHasClassCheck();
                break;
        }
    }

    void start() {
        sock = new PairSocket();
        sock.bind(sockAddr);
        transformer = builder.createAgent(new BaseAgentBuilder(sock, instLoader)).makeRaw();
        //noinspection InfiniteLoopStatement
        while (true) {

            try {
                byte[] requestType = sock.recvBytes();
                assert requestType.length == 1;
                byte req = requestType[0];
                sock.send("ack_req_msg"); // confirm receiving of the message
                if (req == REQ_TYPE_STOP) {
                    log.info("Instrumentor JVM is being stopped!");
                    // finish all the work which needs to be done and then stop the Instrumentor

                    break;
                }
                handleRequest(req);
            } catch (IOException e) {
                // nothing to receive, wait
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    // wake up and continue
                }
            }

        }
        sock.close();
    }


}
