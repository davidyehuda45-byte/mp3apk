package com.example.yt2mp3.domain.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    CONVERTING,
    SAVING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
    val isActive: Boolean get() = this == DOWNLOADING || this == CONVERTING || this == SAVING

    companion object {
        /** Queue state machine guard (PRD §15/§28). Returns false for illegal jumps. */
        fun canTransition(from: DownloadStatus, to: DownloadStatus): Boolean = when (from) {
            QUEUED -> to in setOf(DOWNLOADING, CANCELLED, FAILED)
            DOWNLOADING -> to in setOf(CONVERTING, FAILED, CANCELLED)
            CONVERTING -> to in setOf(SAVING, FAILED, CANCELLED)
            SAVING -> to in setOf(COMPLETED, FAILED, CANCELLED)
            COMPLETED, FAILED, CANCELLED -> to == QUEUED // retry re-queues
        }
    }
}
