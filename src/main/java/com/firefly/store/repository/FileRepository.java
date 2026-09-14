package com.firefly.store.repository;

import com.firefly.store.model.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<FileInfo, String> {
    List<FileInfo> findByExpiryTimeBefore(LocalDateTime time);
    List<FileInfo> findAllByOrderByUploadTimeDesc();
}
