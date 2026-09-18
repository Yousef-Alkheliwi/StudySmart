package com.studysmart.domain;

/** Lifecycle of an uploaded document as it moves through the ingestion pipeline. */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
