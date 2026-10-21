package com.nstut.simplyspeakers.testing;
/** Opt-in evidence from the actual game process, not its Gradle supervisor. */
public final class RuntimeReceipt {
    private RuntimeReceipt() {}
    public static void emit(String role) {
        if(!Boolean.getBoolean("simplyspeakers.requestedPortRuntime"))return;
        String specification=System.getProperty("java.specification.version");
        String major=specification.startsWith("1.")?specification.substring(2):specification;
        System.out.println("SIMPLYSPEAKERS_RUNTIME_JVM role="+role+" major="+major
                +" version="+System.getProperty("java.version")+" vm="+System.getProperty("java.vm.name"));
    }
}
