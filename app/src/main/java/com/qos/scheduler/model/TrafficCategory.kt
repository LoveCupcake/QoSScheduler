package com.qos.scheduler.model

enum class TrafficCategory(val displayName: String, val defaultClass: TrafficClass) {
    VIDEO_CONFERENCING("Video Conferencing", TrafficClass.HIGH),
    ONLINE_GAMING("Online Gaming", TrafficClass.HIGH),
    VOIP("VoIP", TrafficClass.HIGH),
    WEB_BROWSING("Web Browsing", TrafficClass.MEDIUM),
    STREAMING("Streaming", TrafficClass.MEDIUM),
    FILE_TRANSFER("File Transfer", TrafficClass.LOW),
    OS_UPDATE("OS Update", TrafficClass.LOW),
    UNKNOWN("Unknown", TrafficClass.MEDIUM)
}
