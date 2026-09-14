package com.firefly.store.scheduler;

import com.firefly.store.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiryCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryCleanupScheduler.class);

    @Autowired
    private FileService fileService;

    @Scheduled(fixedRate = 3600000, initialDelay = 60000) // Every hour, 1min after startup
    public void cleanupExpiredFiles() {
        log.info("Running expired file cleanup...");
        fileService.cleanupExpiredFiles();
    }
}
