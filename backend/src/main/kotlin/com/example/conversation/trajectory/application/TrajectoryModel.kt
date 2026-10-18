package com.example.conversation.trajectory.application

import com.example.conversation.trajectory.model.*

/** Computation boundary. Implementations do not load session state or persist results. */
interface TrajectoryModel {
    val descriptor: ModelDescriptor
    fun encode(assistantResponse: String): EncodedResponse
    fun compose(earlier: Embedding, later: Embedding, metadata: CompositionMetadata): CompositionPrediction
}
