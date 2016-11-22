//
// Created by Jakub Háva on 08/04/16.
//
#include <jni.h>
#include <jvmti.h>
#include "AgentCallbacks.h"
#include "utils/AgentUtils.h"
#include "utils/JavaUtils.h"
#include "bytecode/ClassParser.h"

using namespace Distrace;
using namespace Distrace::Logging;

void JNICALL AgentCallbacks::cbClassFileLoadHook(jvmtiEnv *jvmti, JNIEnv *jni,
                                                 jclass classBeingRedefined, jobject loader,
                                                 const char *name, jobject protectionDomain,
                                                 jint classDataLen, const unsigned char *classData,
                                                 jint *newClassDataLen, unsigned char **newClassData) {

    AgentUtils::enterCriticalSection(jvmti);
    // Do not handle classes which are being loaded before VM is started
    if (Agent::globalData->vmStarted) {
        int attachStatus = AgentUtils::JNIAttachCurrentThread(jni);
        auto loaderName = JavaUtils::getClassLoaderName(jni, loader);
        // parse the name since name passed to onClassFileLoadHook can be NULL
        auto className = ClassParser::parseJustName(classData, classDataLen);
        log(LOGGER_AGENT_CALLBACKS)->debug("BEFORE LOADING: {} is about to be loaded by {}, is redefined = {} ",
                                          className, loaderName,
                                          classBeingRedefined != NULL);

        // Do not try to instrument classes loaded by ignored class loaders and auxiliary classes from byte buddy
        if (Agent::getInstApi()->shouldContinue(className, loaderName)) {

            if (!Agent::getInstApi()->isClassOnInstrumentor(className)) {
                Agent::getInstApi()->sendClassData(className, classData, classDataLen);
                Agent::getInstApi()->loadDependencies(jni, className, loader, classData, classDataLen);
            }
            Agent::getInstApi()->instrument(jni, loader, className, newClassData, newClassDataLen);
        }

        log(LOGGER_AGENT_CALLBACKS)->debug("AFTER LOADING: {} loaded by {}", className, loaderName);
        AgentUtils::detachJNIFromCurrentThread(attachStatus);
    }
    AgentUtils::exitCriticalSection(jvmti);
}


void JNICALL AgentCallbacks::callbackVMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
    Agent::globalData->vmStarted = JNI_TRUE;
    // Load all necessary helper classes from instrumentor JVM
    Agent::getInstApi()->loadPrepClasses();
    log(LOGGER_AGENT_CALLBACKS)->info("The virtual machine has been initialized!");
}

void JNICALL AgentCallbacks::callbackVMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
    Agent::globalData->vmDead = JNI_TRUE;
    // stop the instrumentor JVM ( only in local mode )
    if (Agent::getArgs()->isRunningInLocalMode()) {
        Agent::globalData->instApi->stop();
    }

    log(LOGGER_AGENT_CALLBACKS)->info("The virtual machine has been terminated!");
}

void JNICALL AgentCallbacks::cbVMStart(jvmtiEnv *jvmti, JNIEnv *jni) {
    log(LOGGER_AGENT_CALLBACKS)->info("The virtual machine has been started!");
}

void JNICALL AgentCallbacks::cbClassLoad(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jclass clazz) {
    log(LOGGER_AGENT_CALLBACKS)->debug("Class: {} loaded", JavaUtils::getClassName(jni, clazz));
}

void JNICALL AgentCallbacks::cbClassPrepare(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jclass clazz) {
    std::string className = JavaUtils::getClassName(jni, clazz);
    log(LOGGER_AGENT_CALLBACKS)->debug("Class: {} prepared", className);
    auto initializers = Agent::getInstApi()->getInitializersFor(className);
    for( auto initializer : initializers){
        log(LOGGER_INSTRUMENTOR_API)->debug("Loading initializer for {}", className);
        // get the class name from the map of instrumented classes, find the loaded type initializer and call the onLoad
        // method on this class
        auto baosClazz = jni->FindClass("java/io/ByteArrayInputStream");
        auto oisClazz = jni->FindClass("java/io/ObjectInputStream");


        auto baosConstructor = jni->GetMethodID(baosClazz, "<init>", "([B)V");
        auto oisConstructor = jni->GetMethodID(oisClazz, "<init>", "(Ljava/io/InputStream;)V");

        auto baosInstance = jni->NewObject(baosClazz, baosConstructor, initializer);
        auto oisInstance = jni->NewObject(oisClazz, oisConstructor, baosInstance);

        auto readObjectMethod = jni->GetMethodID(oisClazz, "readObject","()Ljava/lang/Object;");

        jobject instance = jni->CallObjectMethod(oisInstance, readObjectMethod);
        // call method on instance to ensure loading of interceptor onLoad on instance
        auto initializerClass = jni->GetObjectClass(instance);
        auto onLoadMethod = jni->GetMethodID(initializerClass, "onLoad", "(Ljava/lang/Class;)V");
        jni->CallObjectMethod(instance, onLoadMethod, clazz);
    }

}