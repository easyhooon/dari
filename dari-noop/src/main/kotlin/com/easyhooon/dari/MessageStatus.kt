package com.easyhooon.dari

enum class MessageStatus {
    /** Request has been sent but no response received yet (e.g., waiting for permission dialog) */
    IN_PROGRESS,
    /** Response received successfully */
    SUCCESS,
    /** Response received with an error */
    ERROR,
}