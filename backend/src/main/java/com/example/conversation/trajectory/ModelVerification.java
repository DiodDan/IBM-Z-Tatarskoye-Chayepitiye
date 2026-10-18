package com.example.conversation.trajectory;

import com.example.conversation.trajectory.inference.VerifyModel;

/** Compatibility entry point. All v2 verification lives in VerifyModel. */
@Deprecated
public final class ModelVerification {
    private ModelVerification() {}

    public static void main(String[] args) {
        if (Boolean.getBoolean("trajectory.verifyResources")) {
            VerifyModel.main(new String[]{"classpath:trajectory", args.length > 1 ? args[1] : "3"});
        } else {
            VerifyModel.main(args);
        }
    }
}
