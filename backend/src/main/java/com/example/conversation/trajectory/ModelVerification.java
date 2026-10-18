package com.example.conversation.trajectory;

import com.example.conversation.trajectory.inference.VerifyModel;

/** Java/Gradle entry point for the current window-aware export verifier. */
public final class ModelVerification {
    private ModelVerification() {}

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Usage: ModelVerification BUNDLE [THREADS]");
        String bundle = Boolean.getBoolean("trajectory.verifyResources") ? "classpath:trajectory" : args[0];
        VerifyModel.main(new String[]{bundle, args.length == 2 ? args[1] : "3"});
    }
}
